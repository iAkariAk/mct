package mct.command

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import arrow.core.raise.recover
import arrow.core.right
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import mct.MCTError
import mct.SnbtSyntaxKind
import mct.pointer.DataPointerPattern
import mct.util.snbt.SnbtTag
import org.intellij.lang.annotations.Language

typealias ExtractPatternSet = Map<String, List<CommandExtractPattern>>

operator fun ExtractPatternSet.plus(other: ExtractPatternSet): ExtractPatternSet {
    val result = this.toMutableMap()
    for ((key, value) in other) {
        result[key] = result[key]?.let { value + it } ?: value
    }
    return result
}

@Serializable
data class CommandExtractPattern(
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

context(_: Raise<IndexSelectError>)
private fun selectSnbt(patterns: List<DataPointerPattern>?, arg: MCCommand.Arg): Sequence<SelectResult> {
    val content = arg.content
    val tag = runCatching {
        SnbtTag.decodeFromString(content)
    }.getOrElse {
        raise(IndexSelectError.Parse(content, it))
    }
    return tag.extractTexts(content)
        .filterPointer(patterns)
        .map {
            SelectResult(
                (arg.indices.first + it.indices.first)..(arg.indices.first + it.indices.last),
                it.content,
                it.syntax
            )
        }
}

data class SelectResult(
    override val indices: IntRange, // absolute
    override val content: String,
    override val syntax: SnbtSyntaxKind?
) : StringIndicesWithSyntax

@Serializable
sealed interface IndexSelection {
    context(_: Raise<IndexSelectError>)
    // Left: select parts which are snbt
    // Right:
    //   non-null: the entire is snbt
    //   null: the entire isn't nbt
    fun select(patterns: List<DataPointerPattern>?, arg: MCCommand.Arg): Either<Sequence<SelectResult>, SnbtSyntaxKind?>

    //brigadier:string
    @Serializable
    data object PlainEntire : IndexSelection {
        context(_: Raise<IndexSelectError>)
        override fun select(
            patterns: List<DataPointerPattern>?,
            arg: MCCommand.Arg
        ): Either<Sequence<SelectResult>, SnbtSyntaxKind?> = null.right()
    }

    // minecraft:component || minecraft:nbt_compound_tag || minecraft:nbt_tag || *minecraft:dialog* || minecraft:style
    @Serializable
    data object SnbtEntire : IndexSelection {
        context(_: Raise<IndexSelectError>)
        override fun select(
            patterns: List<DataPointerPattern>?,
            arg: MCCommand.Arg
        ): Either<Sequence<SelectResult>, SnbtSyntaxKind?> =
            recover({ selectSnbt(patterns, arg).left() }, { null.right() })
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
        // about the return sign, refer to [mct.command.IndexSelection.select]
        context(_: Raise<IndexSelectError>)
        fun select(
            pos: Int,
            patterns: List<DataPointerPattern>?,
            arg: MCCommand.Arg
        ): Either<Sequence<SelectResult>, SnbtSyntaxKind?> =
            indexes[pos]?.select(patterns, arg) ?: null.right()
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