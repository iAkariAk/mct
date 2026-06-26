package mct.command

import arrow.core.getOrElse
import arrow.core.raise.context.Raise
import arrow.core.raise.context.either
import arrow.core.raise.recover
import mct.LoggerHolder
import mct.logger
import mct.model.patch.FormatKind
import mct.model.patch.SnbtSyntaxKind
import mct.pointer.*
import mct.text.isTextCompound
import mct.text.isTextCompoundShorthanded
import mct.util.StringIndices
import mct.util.groups2
import mct.util.snbt.SnbtCompound
import mct.util.snbt.SnbtList
import mct.util.snbt.SnbtString
import mct.util.snbt.SnbtTag
import mct.util.surroundedBy


interface StringIndicesWithSyntax : StringIndices {
    override val indices: IntRange // absolute
    override val content: String
    val syntax: SnbtSyntaxKind?
}

data class ExtractedCommandSlice(
    override val indices: IntRange, // absolute
    override val content: String,
    override val syntax: SnbtSyntaxKind?, // null represents the slice isn't a snbt
) : StringIndicesWithSyntax

context(_: LoggerHolder)
fun extractTextFromCommands(
    commandStr: String,
    commandPatterns: ExtractPatternSet = BuiltinCommandPatterns,
    commandDataPatterns: List<DataPointerPattern>? = BuiltinCommandDataPatterns,
    commandRegexPatterns: List<CommandRegexPattern> = emptyList(),
): List<ExtractedCommandSlice> {
    val commands = parseCommands(commandStr)
    val fromCommandPattern = commands.flatMap { command ->
        either {
            extractTextFromCommand(command, commandPatterns, commandDataPatterns)
        }.getOrElse {
            logger.error { "Skip $command due to ${it.message}" }
            emptyList()
        }
    }
    return if (commandRegexPatterns.isNotEmpty()) {
        val fromRegex = commandRegexPatterns.flatMap { p ->
            p.regex.findAll(commandStr).flatMap { result ->
                p.groups.mapNotNull { (group, syntax) ->
                    result.groups2[group]?.let {
                        ExtractedCommandSlice(
                            it.range,
                            it.value,
                            syntax,
                        )
                    }
                }
            }
        }
        if (fromRegex.isEmpty()) fromCommandPattern
        else fromCommandPattern + fromRegex
    } else fromCommandPattern
}


context(_: Raise<IndexSelectError>, _: LoggerHolder)
internal fun extractTextFromCommand(
    command: MCCommand,
    commandPatterns: ExtractPatternSet = BuiltinCommandPatterns,
    commandDataPatterns: List<DataPointerPattern>? = BuiltinCommandDataPatterns,
): List<ExtractedCommandSlice> {
    // return run <command> (1.21+) — similar recursive subcommand extraction
    if (command.name == "execute" || command.name == "return") { // handle nested subcommand after `run`
        val index = command.args.indexOfFirst { it.content == "run" }
        val subBeginPos = index + 1
        if (subBeginPos == command.args.size) return emptyList()
        val rawSubcommand = command.args.subList(subBeginPos, command.args.size)
        val subName = rawSubcommand.first()
        val subBeginIndexRel = subName.relativeIndices.first
        val subBeginIndexAbs = command.indices.first + subBeginIndexRel
        val subIndicesAbs = subBeginIndexAbs..command.indices.last
        val subRaw = command.raw.substring(subBeginIndexRel - command.trimOffset)
        val subArgs = rawSubcommand.subList(1, rawSubcommand.size).map { arg ->
            MCCommand.Arg(
                relativeIndices = (arg.relativeIndices.first - subBeginIndexRel)..(arg.relativeIndices.last - subBeginIndexRel),
                indices = arg.indices,
                content = arg.content
            )
        }
        val subCommand = MCCommand(subRaw, subName.content, subIndicesAbs, subArgs)
        return extractTextFromCommand(subCommand, commandPatterns, commandDataPatterns)
    }
    val fromIntrinsic = CommandExtractorIntrinsic.extract(command)
    val fromPattern = (commandPatterns[command.name]?.asSequence() ?: emptySequence())
        .filter { it.preCondition.matches(command) }
        .flatMap { pattern ->
            when (val selector = pattern.selector) {
                is IndexSelector.Greedy -> {
                    val (relRange, absRange) = computeGreedyRange(command, selector)
                    ExtractedCommandSlice(
                        absRange,
                        command.raw.substring(relRange),
                        null
                    ).let(::sequenceOf)
                }

                is IndexSelector.NonGreedy ->
                    command.args.asSequence()
                        .withIndex()
                        .filter { (index, _) -> selector.matches(index + 1) }
                        .filter { (_, arg) -> pattern.postCondition.matches(command, arg) }
                        .flatMap { (index, arg) ->
                            recover(
                                block = {
                                    selector.select(index + 1, commandDataPatterns, arg)?.map {
                                        ExtractedCommandSlice(it.indices, it.content, it.syntax)
                                    }
                                },
                                recover = {
                                    logger.error { "Selection fails: ${it.message}" }
                                    null
                                }
                            ) ?: ExtractedCommandSlice(
                                arg.indices,
                                arg.content,
                                null
                            ).let(::listOf) // feedback
                        }
            }
        }.toList()
    return fromIntrinsic + fromPattern
}

// Refer to mct.region.ExtractKt.extractTexts
// due to using IR dragging slow performance

internal data class PointerWithExtensionForSnbt(
    val pointer: DataPointer,
    override val indices: IntRange, // relate to the arg
    override val content: String,
    val kind: FormatKind,
    override val syntax: SnbtSyntaxKind,
) : StringIndicesWithSyntax

internal inline fun Sequence<PointerWithExtensionForSnbt>.filterPointer(patterns: Iterable<DataPointerPattern>?) =
    filter { (ptr, _, _) -> ptr.matches(patterns) }

internal fun SnbtTag.extractTextsByPointer(snbt: String): Sequence<PointerWithExtensionForSnbt> = when (this) {
    is SnbtList -> if (isTextCompound()) {
        sequenceOf(
            PointerWithExtensionForSnbt(
                DataPointer.Terminator,
                indices,
                snbt.substring(indices),
                FormatKind.SnbtStr,
                syntax = SnbtSyntaxKind.List
            )
        )
    } else {
        asSequence().withIndex().flatMap { (index, tag) ->
            tag.extractTextsByPointer(snbt).map {
                it.copy(pointer = it.pointer.markArray(index))
            }
        } // wrap inner pointer
    }

    is SnbtCompound -> if (isTextCompound() || isTextCompoundShorthanded()) {
        sequenceOf(
            PointerWithExtensionForSnbt(
                DataPointer.Terminator,
                indices,
                snbt.substring(indices),
                FormatKind.SnbtStr,
                syntax = SnbtSyntaxKind.Compound
            )
        )
    } else asSequence().flatMap { (key, value) ->
        value.extractTextsByPointer(snbt).map {
            it.copy(pointer = it.pointer.markMap(key))
        } // wrap inner pointer
    }

    is SnbtString -> sequenceOf(
        PointerWithExtensionForSnbt(
            DataPointer.Terminator,
            indices,
            raw,
            FormatKind.PlainStr,
            syntaxKind
        )
    )

    else -> emptySequence()
}

private fun computeGreedyRange(
    command: MCCommand,
    selector: IndexSelector.Greedy,
): Pair<IntRange, IntRange> {
    val commandBeginIndex = command.indices.first
    val beginIndexRelative =
        if (selector.position == 0) {
            if (command.name.length == command.raw.length) command.name.length
            else command.args.firstOrNull()?.relativeIndices?.first?.minus(command.trimOffset)
                ?: (command.name.length + command.raw.indexOfFirst { it != ' ' })
        } else command[selector.position].relativeIndices.first - command.trimOffset
    val endIndexRelative = command.raw.length - 1
    val relRange = beginIndexRelative..endIndexRelative
    val absRange = (commandBeginIndex + command.trimOffset + beginIndexRelative)..
            (commandBeginIndex + command.trimOffset + endIndexRelative)
    return Pair(relRange, absRange)
}


internal object CommandExtractorIntrinsic {
    // https://minecraft.wiki/w/Target_selectors
    private val SELECTOR_REGEX = Regex("""^@[praesn]\[.*]$""")
    private val SELECTOR_NAME_REGEX = Regex("""name=!?("(?:\\.|.)*?"|'.*?'|[\w:]*)[,\]]""")
    fun extractFromTargetSelector(args: List<MCCommand.Arg>): List<ExtractedCommandSlice> = args.asSequence()
        .filter { SELECTOR_REGEX.matches(it.content) }
        .mapNotNull { arg ->
            SELECTOR_NAME_REGEX.find(arg.content)?.let { result ->
                val negative = result.value.startsWith("name=!")
                val value = result.groupValues[1]
                ExtractedCommandSlice(
                    (arg.indices.first + result.range.first + 5 + if (negative) 1 else 0)..<arg.indices.first + result.range.last,
                    value,
                    value.inferSyntaxKind()
                )
            }
        }
        .toList()


    fun extract(command: MCCommand): List<ExtractedCommandSlice> =
        extractFromTargetSelector(command.args)
}


private fun String.inferSyntaxKind(): SnbtSyntaxKind = when {
    surroundedBy('\'') -> SnbtSyntaxKind.SingleQuoteString
    surroundedBy('\"') -> SnbtSyntaxKind.DoubleQuoteString
    else -> SnbtSyntaxKind.LiteralString
}