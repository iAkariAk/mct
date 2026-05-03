package mct.dp.mcfunction

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import mct.serializer.IntRangeSerializable
import org.intellij.lang.annotations.Language

typealias ExtractPatternSet = Map<String, List<ExtractPattern>>

operator fun ExtractPatternSet.plus(other: ExtractPatternSet): ExtractPatternSet {
    val result = this.toMutableMap()
    for ((key, value) in other) {
        result[key]?.let {
            value + it
        } ?: value
    }
    return result
}

@Serializable
data class ExtractPattern(
    val command: String,
    val preCondition: PreCondition,
    val selected: IndexSelector,
    val postCondition: PostCondition
)

fun interface PreCondition {
    fun matches(command: MCCommand): Boolean

    companion object {
        @Serializable
        @SerialName("any")
        data object Any : PreCondition {
            override fun matches(command: MCCommand) = true
        }


        @Serializable
        @SerialName("and")
        data class And(val conditions: List<PreCondition>) : PreCondition {
            override fun matches(command: MCCommand) = conditions.all { it.matches(command) }
        }

        @Serializable
        @SerialName("or")
        data class Or(val conditions: List<PreCondition>) : PreCondition {
            override fun matches(command: MCCommand) = conditions.any { it.matches(command) }
        }

        @Serializable
        @SerialName("none")
        data class None(val conditions: List<PreCondition>) : PreCondition {
            override fun matches(command: MCCommand) = conditions.none { it.matches(command) }
        }

        @Serializable
        @SerialName("with_size")
        data class WithSize(val size: Int, val strict: Boolean = false) : PreCondition {
            override fun matches(command: MCCommand) =
                if (strict) size == command.args.size else size >= command.args.size
        }

        @Serializable
        @SerialName("regex")
        data class Regex(@field:Language("RegExp") val regex: String) : PreCondition {
            private val _regex by lazy { regex.toRegex() }
            override fun matches(command: MCCommand) = _regex.containsMatchIn(command.raw)
        }
    }
}

sealed interface IndexSelector {
    @Serializable
    @SerialName("greedy")
    data class Greedy(val position: Int) : IndexSelector // when position is 0, select all args

    @SerialName("non_greedy")
    fun interface NonGreedy : IndexSelector {
        // 1-based index
        fun matches(index: Int): Boolean

        companion object {
            @Serializable
            @SerialName("any")
            data object Any : NonGreedy {
                override fun matches(index: Int) = true
            }

            @Serializable
            @SerialName("and")
            data class And(val conditions: List<NonGreedy>) : NonGreedy {
                override fun matches(index: Int) = conditions.all { it.matches(index) }
            }

            @Serializable
            @SerialName("or")
            data class Or(val conditions: List<NonGreedy>) : NonGreedy {
                override fun matches(index: Int) = conditions.any { it.matches(index) }
            }

            @Serializable
            @SerialName("none")
            data class None(val conditions: List<NonGreedy>) : NonGreedy {
                override fun matches(index: Int) = conditions.none { it.matches(index) }
            }

            @Serializable
            @SerialName("range")
            data class Range(val range: IntRangeSerializable) : NonGreedy {
                override fun matches(index: Int) = index in range
            }

            @Serializable
            @SerialName("index")
            data class Special(val specials: List<Int>) : NonGreedy {
                override fun matches(index: Int) = index in specials
            }
        }
    }
}

fun interface PostCondition {
    fun matches(command: MCCommand, arg: MCCommand.Arg): Boolean

    companion object {
        @Serializable
        @SerialName("any")
        data object Any : PostCondition {
            override fun matches(command: MCCommand, arg: MCCommand.Arg) = true
        }

        @Serializable
        @SerialName("and")
        data class And(val conditions: List<PostCondition>) : PostCondition {
            override fun matches(command: MCCommand, arg: MCCommand.Arg) = conditions.all { it.matches(command, arg) }
        }

        @Serializable
        @SerialName("or")
        data class Or(val conditions: List<PostCondition>) : PostCondition {
            override fun matches(command: MCCommand, arg: MCCommand.Arg) = conditions.any { it.matches(command, arg) }
        }

        @Serializable
        @SerialName("none")
        data class None(val conditions: List<PostCondition>) : PostCondition {
            override fun matches(command: MCCommand, arg: MCCommand.Arg) = conditions.none { it.matches(command, arg) }
        }


        @Serializable
        @SerialName("at")
        data class At(val position: Int, val condition: PostCondition) : PostCondition {
            override fun matches(command: MCCommand, arg: MCCommand.Arg) = condition.matches(command, command[position])
        }

        @Serializable
        @SerialName("regex")
        data class MatchRegex(val regex: String) : PostCondition {
            private val _regex by lazy { regex.toRegex() }
            override fun matches(command: MCCommand, arg: MCCommand.Arg): Boolean =
                _regex.containsMatchIn(arg.content)
        }

        @Serializable
        @SerialName("contain")
        data class Contain(val content: String) : PostCondition {
            override fun matches(command: MCCommand, arg: MCCommand.Arg): Boolean =
                content in arg.content
        }

        @Serializable
        @SerialName("equal")
        data class Equal(val content: String) : PostCondition {
            override fun matches(command: MCCommand, arg: MCCommand.Arg): Boolean =
                content == arg.content
        }
    }
}

val extractPatternModule = SerializersModule {
    polymorphic(PreCondition::class) {
        subclass(PreCondition.Companion.Any::class)
        subclass(PreCondition.Companion.And::class)
        subclass(PreCondition.Companion.Or::class)
        subclass(PreCondition.Companion.None::class)
        subclass(PreCondition.Companion.WithSize::class)
        subclass(PreCondition.Companion.Regex::class)
    }

    polymorphic(IndexSelector::class) {
        subclass(IndexSelector.Greedy::class)
        polymorphic(IndexSelector.Greedy::class) {
            subclass(IndexSelector.NonGreedy.Companion.Any::class)
            subclass(IndexSelector.NonGreedy.Companion.And::class)
            subclass(IndexSelector.NonGreedy.Companion.Or::class)
            subclass(IndexSelector.NonGreedy.Companion.None::class)
            subclass(IndexSelector.NonGreedy.Companion.Range::class)
            subclass(IndexSelector.NonGreedy.Companion.Special::class)
        }
    }


    polymorphic(PostCondition::class) {
        subclass(PostCondition.Companion.Any::class)
        subclass(PostCondition.Companion.And::class)
        subclass(PostCondition.Companion.Or::class)
        subclass(PostCondition.Companion.None::class)
        subclass(PostCondition.Companion.At::class)
        subclass(PostCondition.Companion.MatchRegex::class)
        subclass(PostCondition.Companion.Contain::class)
        subclass(PostCondition.Companion.Equal::class)
    }
}