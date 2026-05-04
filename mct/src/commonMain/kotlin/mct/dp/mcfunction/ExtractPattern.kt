package mct.dp.mcfunction

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import arrow.core.raise.recover
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import mct.FormatKind
import mct.MCTError
import mct.region.BuiltinRegionPatterns
import mct.region.extractTexts
import mct.region.filterPointer
import mct.serializer.Snbt
import mct.util.StringIndices
import mct.util.findAll
import net.benwoodworth.knbt.NbtTag
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

sealed interface IndexSelectError : MCTError {
    data class Parse(
        val raw: String,
        val reason: Throwable
    ) : IndexSelectError {
        override val message = "When parsing $raw, get ${reason.message}"
    }
}

private const val ANYWAY_PLACEHOLDER = "😭NEWLINE😭"

context(_: Raise<IndexSelectError>)
private fun selectSnbt(content: String): Sequence<StringIndices> {
    val hacky = content.replace("\\n", ANYWAY_PLACEHOLDER)
    val tag = runCatching {
        // Anyhow, knbt cannot handle inconsistent List,
        // which signifies complex TextCompounds including String and Compound causes ParseError.
        Snbt.decodeFromString<NbtTag>(hacky)
    }.getOrElse {
        raise(IndexSelectError.Parse(hacky, it))
    }
    return tag.extractTexts()
        .filterPointer(BuiltinRegionPatterns)
        .filter { it.kind == FormatKind.Json } // TODO: perhaps should support NBT
        .flatMap { pwe ->
            val raw = pwe.content
                .replace(ANYWAY_PLACEHOLDER, "\\n")
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
            content.findAll(raw).map {
                StringIndices(it, pwe.content)
            }
        }
}

@Serializable
sealed interface IndexSelection {
    context(_: Raise<IndexSelectError>)
    fun select(content: String): Sequence<StringIndices>?

    @Serializable
    data object PlainEntire : IndexSelection {
        context(_: Raise<IndexSelectError>)
        override fun select(content: String): Sequence<StringIndices>? = null
    }

    @Serializable
    data object SnbtEntire : IndexSelection {
        context(_: Raise<IndexSelectError>)
        override fun select(content: String): Sequence<StringIndices>? =
            recover({ selectSnbt(content) }, { PlainEntire.select(content) })
    }
}

@Serializable
sealed interface IndexSelector {
    @Serializable
    @SerialName("greedy")
    data class Greedy(val position: Int) : IndexSelector // when position is 0, select all args

    @Serializable
    @SerialName("non_greedy")
    data class NonGreedy(
        val indexes: Map<Int, IndexSelection?>,
    ) : IndexSelector {
        // 1-based index
        fun matches(pos: Int) = pos in indexes

        // select parts of the entire arg, and extract field if selection is as to snbt
        context(_: Raise<IndexSelectError>)
        fun select(pos: Int, str: String): Sequence<StringIndices>? =
            indexes[pos]?.select(str) ?: return null
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
        subclass(IndexSelector.NonGreedy::class)
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