package mct.dp.mcfunction

import arrow.core.getOrElse
import arrow.core.raise.context.Raise
import arrow.core.raise.context.either
import mct.DatapackExtraction
import mct.DatapackExtractionGroup
import mct.Logger
import mct.dp.Extractor
import mct.util.StringIndices


internal fun MCFunctionExtractor(
    patterns: ExtractPatternSet = BuiltinMCFPatterns
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


context(logger: Logger)
internal fun extractTextMCF(
    mcf: String,
    source: String,
    path: String,
    patterns: ExtractPatternSet = BuiltinMCFPatterns
): DatapackExtractionGroup {
    val mcfunctions = parseMCFunction(mcf)
    logger.debug { "Parsed ${mcfunctions.size} commands in $path" }
    val extractedArgs = mcfunctions.asSequence().flatMap { command ->
        either {
            extractTextFromCommand(command, patterns)
        }.getOrElse {
            logger.error { "Skip $command due to ${it.message}" }
            emptyList()
        }
    }
    val extractions = extractedArgs.map { extracted ->
        DatapackExtraction.MCFunction(
            indices = extracted.indices,
            content = extracted.content
        )
    }.toList()
    logger.debug { "Extracted ${extractions.size} texts from $path" }
    return DatapackExtractionGroup(
        source = source,
        path = path,
        extractions = extractions
    )
}


context(_: Raise<IndexSelectError>)
internal fun extractTextFromCommand(
    command: MCCommand,
    patterns: ExtractPatternSet = BuiltinMCFPatterns
): List<StringIndices> {
    if (command.name == "execute") { // handle nested subcommand after `run`
        val index = command.args.indexOfFirst { it.content == "run" }
        val subBeginPos = index + 1
        if (subBeginPos == command.args.size) return emptyList()
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
        return extractTextFromCommand(subCommand, patterns)
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
                            else command.args.firstOrNull()?.relativeIndices?.first
                                ?: (command.name.length + command.raw.removePrefix(command.name)
                                    .indexOfFirst { it != ' ' })
                        } else command[selector.position].relativeIndices.first
                    val endIndexRelative = command.raw.length - 1
                    val relRange = beginIndexRelative..endIndexRelative
                    val absRange = (commandBeginIndex + beginIndexRelative)..(commandBeginIndex + endIndexRelative)
                    StringIndices(absRange, command.raw.substring(relRange)).let(::sequenceOf)
                }

                is IndexSelector.NonGreedy ->
                    command.args.asSequence()
                        .withIndex()
                        .filter { (index, _) -> selector.matches(index + 1) }
                        .filter { (_, arg) -> pattern.postCondition.matches(command, arg) }
                        .flatMap { (index, arg) ->
                            val selections =
                                selector.select(index + 1, arg.content) ?: return@flatMap sequenceOf(arg)
                            selections.map {
                                val entire = arg.indices
                                val part = it.indices
                                val indices = (entire.first + part.first)..(entire.first + part.last)
                                StringIndices(indices, it.content)
                            }
                        }
            }
        }.toList()
}
