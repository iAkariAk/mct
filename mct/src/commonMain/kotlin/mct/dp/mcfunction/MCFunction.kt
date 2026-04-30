package mct.dp.mcfunction

import mct.DatapackExtraction
import mct.DatapackExtractionGroup
import mct.Logger
import mct.dp.Extractor


internal fun MCFunctionExtractor(
    patterns: ExtractPatternSet = BuiltinPatterns
) = Extractor("MCFunction", ".mcfunction") { env, zfs, zpath, path ->
    val text = zfs.read(zpath) { readUtf8() }
    context(env.logger) {
        extractTextMCF(
            text,
            source = path.name,
            path = zpath.normalized().toString(),
            patterns = patterns,
        )
    }
}

private sealed interface Extracted {
    data class Arg(val arg: MCCommand.Arg) : Extracted
    data class GreedyString(val indices: IntRange, val content: String) : Extracted
}

context(logger: Logger)
internal fun extractTextMCF(
    mcf: String,
    source: String,
    path: String,
    patterns: ExtractPatternSet = BuiltinPatterns
): DatapackExtractionGroup {
    val mcfunctions = parseMCFunction(mcf)
    val extractedArgs: Sequence<Extracted> = mcfunctions.asSequence().flatMap { command ->
        extractTextFromCommand(patterns, command)
    }
    return DatapackExtractionGroup(
        source = source,
        path = path,
        extractions = extractedArgs.map { extracted ->
            when (extracted) {
                is Extracted.Arg -> {
                    val arg = extracted.arg
                    DatapackExtraction.MCFunction(
                        indices = arg.indices,
                        content = arg.content
                    )
                }

                is Extracted.GreedyString -> DatapackExtraction.MCFunction(
                    indices = extracted.indices,
                    content = extracted.content
                )
            }
        }.toList()
    )
}


private fun extractTextFromCommand(
    patterns: ExtractPatternSet,
    command: MCCommand
): Sequence<Extracted> {
    if (command.name == "execute") { // handle nested subcommand after `run`
        val index = command.args.indexOfFirst { it.content == "run" }
        val subBeginPos = index + 1
        if (subBeginPos == command.args.size) return emptySequence()
        val rawSubcommand = command.args.subList(subBeginPos, command.args.size)
        val subName = rawSubcommand.first()
        val subBeginIndexRel = subName.relativeIndices.first
        val subBeginIndexAbs = command.indices.first + subBeginIndexRel
        val subIndicesAbs = subBeginIndexAbs..command.indices.last
        val subRaw = command.raw.substring(subBeginIndexRel)
        val subArgs = rawSubcommand.subList(1, rawSubcommand.size).map { arg ->
            MCCommand.Arg(
                relativeIndices = (arg.relativeIndices.first - subBeginIndexRel)..(arg.relativeIndices.last - subBeginIndexRel),
                indices = arg.indices,
                content = arg.content
            )
        }
        val subCommand = MCCommand(subRaw, subName.content, subIndicesAbs, subArgs)
        return extractTextFromCommand(patterns, subCommand)
    }
    return (patterns[command.name]?.asSequence() ?: emptySequence())
        .filter { it.preCondition.matches(command) }
        .flatMap { pattern ->
            when (val selector = pattern.selected) {
                is IndexSelector.Greedy -> {
                    val commandBeginIndex = command.indices.first
                    val beginIndexRelative =
                        if (selector.position == 0) {
                            if (command.name.length == command.raw.length) command.name.length
                            else command.name.length + 1
                        } else command[selector.position].relativeIndices.first
                    val endIndexRelative = command.raw.length - 1
                    val relRange = beginIndexRelative..endIndexRelative
                    val absRange = (commandBeginIndex + beginIndexRelative)..(commandBeginIndex + endIndexRelative)
                    Extracted.GreedyString(absRange, command.raw.substring(relRange)).let(::sequenceOf)
                }

                is IndexSelector.NonGreedy ->
                    command.args.asSequence()
                        .withIndex()
                        .filter { (index, _) -> selector.matches(index + 1) }
                        .filter { pattern.postCondition.matches(command, it.value) }
                        .map { Extracted.Arg(it.value) }

            }
        }
}
