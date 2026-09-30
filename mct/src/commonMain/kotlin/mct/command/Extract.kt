package mct.command

import arrow.core.getOrElse
import arrow.core.raise.context.Raise
import arrow.core.raise.context.either
import arrow.core.raise.recover
import mct.LoggerHolder
import mct.MCTPattern
import mct.logger
import mct.model.patch.FormatKind
import mct.model.patch.SnbtSyntaxKind
import mct.model.patch.inferFormatKind
import mct.model.patch.inferSyntaxKind
import mct.model.text.isTextComponent
import mct.model.text.isTextComponentShorthanded
import mct.pointer.DataPointer
import mct.pointer.markArray
import mct.pointer.markMap
import mct.util.StringIndices
import mct.util.groups2
import mct.util.offset
import mct.util.overlapsWith
import mct.util.snbt.SnbtCompound
import mct.util.snbt.SnbtList
import mct.util.snbt.SnbtString
import mct.util.snbt.SnbtTag


interface StringIndicesWithSyntaxFormat : StringIndices {
    override val indices: IntRange // absolute
    override val content: String
    val syntax: SnbtSyntaxKind?
    val format: FormatKind
}

data class ExtractedCommandSlice(
    override val indices: IntRange, // absolute
    override val content: String,
    override val syntax: SnbtSyntaxKind?, // null represents the slice isn't a snbt
    override val format: FormatKind
) : StringIndicesWithSyntaxFormat

context(_: LoggerHolder)
fun extractTextFromCommands(
    commandStr: String,
    patterns: MCTPattern = MCTPattern.Default,
): List<StringIndicesWithSyntaxFormat> {
    val commands = parseCommands(commandStr)
    val fromCommandPattern = commands.flatMap { command ->
        either {
            extractTextFromCommand(command, patterns)
        }.getOrElse {
            logger.error { "Skip $command due to ${it.message}" }
            emptyList()
        }
    }
    return if (patterns.commandRegex.isNotEmpty()) {
        val fromRegex = patterns.commandRegex.flatMap { p ->
            p.regex.findAll(commandStr).flatMap { result ->
                p.groups.mapNotNull { (group, info) ->
                    result.groups2[group]?.let {
                        ExtractedCommandSlice(
                            it.range,
                            it.value,
                            info?.syntax,
                            info?.format ?: it.value.inferFormatKind(syntax = info?.syntax),
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
    patterns: MCTPattern = MCTPattern.Default,
    useIntrinsic: Boolean = true
): List<StringIndicesWithSyntaxFormat> {
    // return run <command> (1.21+) — similar recursive subcommand extraction
    fun mergeResult(fromPattern: List<StringIndicesWithSyntaxFormat>) =
        if (useIntrinsic) CommandExtractorIntrinsic.extract(command).filter { efi ->
            fromPattern.none { efp -> efi.indices overlapsWith efp.indices }
        }.toList().plus(fromPattern) else fromPattern

    if (command.name == "execute" || command.name == "return") { // handle nested subcommand after `run`
        val index = command.args.indexOfFirst { it.content == "run" }
        // legacy: https://zh.minecraft.wiki/w/%E5%91%BD%E4%BB%A4/execute/%E6%97%A7%E7%89%88
        val isLegacy = index == -1
        val subBeginIndex = if (isLegacy) getLegacyExecuteSubBeginIndex(command) else index + 1

        if (subBeginIndex >= 0 && subBeginIndex < command.args.size) {
            val rawSubcommand = command.args.subList(subBeginIndex, command.args.size)
            val subNameArg = rawSubcommand.first()
            val subBeginIndexRel = subNameArg.relativeIndices.first
            val subName =
                subNameArg.content.removePrefix("/") // legacy execute allow subcommand to begin with the slash
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
            val subCommand = MCCommand(subRaw, subName, subIndicesAbs, subArgs, false)
            val fromPattern = extractTextFromCommand(subCommand, patterns, false)
            return mergeResult(fromPattern)
        }
    }
    val fromPattern = (patterns.command[command.name]?.asSequence() ?: emptySequence())
        .filter { it.preCondition.matches(command) }
        .flatMap { pattern ->
            when (val selector = pattern.selector) {
                is IndexSelector.Greedy -> {
                    val (relRange, absRange) = computeGreedyRange(command, selector)
                    (ExtractedCommandSlice(
                        absRange,
                        command.raw.substring(relRange),
                        null,
                        PlainStr
                    ) as StringIndicesWithSyntaxFormat).let(::sequenceOf)
                }

                is IndexSelector.NonGreedy ->
                    command.args.asSequence()
                        .withIndex()
                        .filter { (index, arg) ->
                            selector.matches(command.args.size, index + 1)
                                    && pattern.postCondition.matches(command, arg)
                        }
                        .flatMap { (index, arg) ->
                            recover(
                                block = {
                                    when (val results = selector.select(command.args.size, index + 1, patterns, arg)) {
                                        is SelectResult.Entire -> ExtractedCommandSlice(
                                            arg.indices,
                                            arg.content,
                                            results.syntax,
                                            results.format
                                        ).let(::sequenceOf)

                                        is SelectResult.Portions -> results.portions.asSequence()
                                        None -> emptySequence()
                                    }
                                },
                                recover = {
                                    logger.error { "Selection fails: ${it.message}" }
                                    ExtractedCommandSlice(
                                        arg.indices,
                                        arg.content,
                                        null,
                                        PlainStr
                                    ).let(::sequenceOf)
                                }
                            )
                        }
            }
        }.toList()

    return mergeResult(fromPattern)
}

// Refer to mct.region.ExtractKt.extractTexts
// due to using IR dragging slow performance

internal data class PointerWithExtensionForSnbt(
    override val indices: IntRange, // relate to the arg
    override val content: String,
    val pointer: DataPointer,
    override val syntax: SnbtSyntaxKind,
    override val format: FormatKind,  // the [format] is the content inside the quotation if [syntax] is any quote type
) : StringIndicesWithSyntaxFormat

internal fun SnbtTag.extractTextsByPointer(snbt: String, snbtOffset: Int = 0): Sequence<PointerWithExtensionForSnbt> =
    when (this) {
        is SnbtList -> if (isTextComponent()) {
            sequenceOf(
                PointerWithExtensionForSnbt(
                    indices,
                    snbt.substring(indices.offset(-snbtOffset)),
                    DataPointer.Terminator,
                    syntax = SnbtSyntaxKind.List,
                    format = SnbtStr,
                )
            )
        } else {
            asSequence().withIndex().flatMap { (index, tag) ->
                tag.extractTextsByPointer(snbt, snbtOffset).map {
                    it.copy(pointer = it.pointer.markArray(index))
                }
            } // wrap inner pointer
        }

        is SnbtCompound -> if (isTextComponent() || isTextComponentShorthanded()) {
            sequenceOf(
                PointerWithExtensionForSnbt(
                    indices,
                    snbt.substring(indices.offset(-snbtOffset)),
                    DataPointer.Terminator,
                    syntax = SnbtSyntaxKind.Compound,
                    format = SnbtStr,
                )
            )
        } else asSequence().flatMap { (key, value) ->
            value.extractTextsByPointer(snbt, snbtOffset).map {
                it.copy(pointer = it.pointer.markMap(key))
            } // wrap inner pointer
        }

        is SnbtString -> sequenceOf(
            PointerWithExtensionForSnbt(
                indices,
                raw,
                DataPointer.Terminator,
                syntax = syntax,
                format = raw.inferFormatKind(syntax = syntax),
            )
        )

        else -> emptySequence()
    }

private fun getLegacyExecuteSubBeginIndex(command: MCCommand): Int =
    // execute <entity> <x> <y> <z> <command>
    if (command.args.size >= 5) {
        // execute <entity> <x> <y> <z> detect <x2> <y2> <z2> <block> <data|state> <command>
        if (command.args.size >= 11 && command.args[5].content == "detect") 10 else 4
    } else -1

private fun computeGreedyRange(
    command: MCCommand,
    selector: IndexSelector.Greedy,
): Pair<IntRange, IntRange> {
    val commandBeginIndex = command.indices.first
    val position = selector.normalizePosition(command.args.size)
    val beginIndexRelative = if (position == 0) {
        if (command.name.length == command.raw.length) command.name.length
        else command.args.firstOrNull()?.relativeIndices?.first?.minus(command.trimOffset)
            ?: (command.name.length + command.raw.indexOfFirst { it != ' ' })
    } else command[position].relativeIndices.first - command.trimOffset
    val endIndexRelative = command.raw.length - 1
    val relRange = beginIndexRelative..endIndexRelative
    val absRange = (commandBeginIndex + command.trimOffset + beginIndexRelative)..
            (commandBeginIndex + command.trimOffset + endIndexRelative)
    return Pair(relRange, absRange)
}


internal object CommandExtractorIntrinsic {
    // https://minecraft.wiki/w/Target_selectors
    fun extractFromTargetSelector(args: List<MCCommand.Arg>): Sequence<StringIndicesWithSyntaxFormat> =
        args.asSequence()
            .filter { it.content.isTargetSelector() }
            .mapNotNull { arg ->
                val (indices, name) = arg.content.getTargetSelectorName() ?: return@mapNotNull null
                val syntax = name.inferSyntaxKind()
                ExtractedCommandSlice(
                    indices = indices.offset(arg.indices.first),
                    content = name,
                    syntax = syntax,
                    format = name.inferFormatKind(syntax = syntax),
                )
            }


    fun extract(command: MCCommand): Sequence<StringIndicesWithSyntaxFormat> =
        extractFromTargetSelector(command.args)
}
