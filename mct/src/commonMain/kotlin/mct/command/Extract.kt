package mct.command

import arrow.core.raise.context.Raise
import mct.SyntaxKind
import mct.pointer.DataPointerPattern
import mct.util.StringIndices
import mct.util.surroundedBy


data class ExtractedCommandSlice(
    override val indices: IntRange,
    override val content: String,
    val syntax: SyntaxKind
) : StringIndices

// https://minecraft.wiki/w/Target_selectors
private val SELECTOR_REGEX = Regex("""^@[praesn]\[.*]$""")
private val SELECTOR_NAME_REGEX = Regex("""name=!?("(?:\\.|.)*?"|'.*?'|[\w:]*)[,\]]?""")
internal fun extractTextFromTargetSelector(args: List<MCCommand.Arg>): List<ExtractedCommandSlice> = args.asSequence()
    .filter { SELECTOR_REGEX.matches(it.content) }
    .mapNotNull { arg ->
        SELECTOR_NAME_REGEX.find(arg.content)?.let { result ->
            val negative = result.value.startsWith("name=!")
            val value = result.groupValues[1]
            val syntax = when {
                value.surroundedBy('\'') -> SyntaxKind.SingleQuoteWrapped
                value.surroundedBy('\"') -> SyntaxKind.DoubleQuoteWrapped
                else -> SyntaxKind.Literal
            }
            ExtractedCommandSlice(
                (arg.indices.first + result.range.first + 5 + if (negative) 1 else 0)..<arg.indices.first + result.range.last,
                value,
                syntax
            )
        }
    }
    .toList()


context(_: Raise<IndexSelectError>)
internal fun extractTextFromCommand(
    command: MCCommand,
    mcfPatterns: ExtractPatternSet = BuiltinMCFPatterns,
    mcfDataPatterns: List<DataPointerPattern>? = BuiltinMCFunctionDataPatterns
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
        return extractTextFromCommand(subCommand, mcfPatterns, mcfDataPatterns)
    }
    return (mcfPatterns[command.name]?.asSequence() ?: emptySequence())
        .filter { it.preCondition.matches(command) }
        .flatMap { pattern ->
            when (val selector = pattern.selected) {
                is IndexSelector.Greedy -> {
                    val (relRange, absRange) = computeGreedyRange(command, selector)
                    ExtractedCommandSlice(
                        absRange,
                        command.raw.substring(relRange),
                        SyntaxKind.Literal
                    ).let(::sequenceOf)
                }

                is IndexSelector.NonGreedy ->
                    command.args.asSequence()
                        .withIndex()
                        .filter { (index, _) -> selector.matches(index + 1) }
                        .filter { (_, arg) -> pattern.postCondition.matches(command, arg) }
                        .flatMap { (index, arg) ->
                            val selections =
                                selector.select(index + 1, mcfDataPatterns, arg.content) ?: return@flatMap sequenceOf(
                                    ExtractedCommandSlice(arg.indices, arg.content, SyntaxKind.Literal)
                                )
                            selections.map {
                                val entire = arg.indices
                                val part = it.indices
                                val indices = (entire.first + part.first)..(entire.first + part.last)
                                ExtractedCommandSlice(indices, it.content, SyntaxKind.Literal)
                            }
                        }
            }
        }.toList()
}

private fun computeGreedyRange(
    command: MCCommand,
    selector: IndexSelector.Greedy
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
