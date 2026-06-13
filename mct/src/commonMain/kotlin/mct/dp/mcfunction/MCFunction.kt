package mct.dp.mcfunction

import arrow.core.getOrElse
import arrow.core.raise.context.Raise
import arrow.core.raise.context.either
import mct.DatapackExtraction
import mct.DatapackExtractionGroup
import mct.Logger
import mct.dp.Extractor
import mct.pointer.DataPointerPattern
import mct.util.StringIndices


internal fun MCFunctionExtractor(
    mcfPatterns: ExtractPatternSet = BuiltinMCFPatterns,
    mcfDataPatterns: List<DataPointerPattern>? = BuiltinMCFunctionDataPatterns
) = Extractor("MCFunction", ".mcfunction") { env, zfs, zpath, path ->
    val text = zfs.read(zpath) { readUtf8() }
    context(env.logger) {
        extractTextMCF(
            text,
            source = path.name,
            path = zpath.normalized().toString(),
            mcfPatterns = mcfPatterns,
            mcfDataPatterns = mcfDataPatterns
        )
    }
}


context(logger: Logger)
internal fun extractTextMCF(
    mcf: String,
    source: String,
    path: String,
    mcfPatterns: ExtractPatternSet = BuiltinMCFPatterns,
    mcfDataPatterns: List<DataPointerPattern>? = BuiltinMCFunctionDataPatterns
): DatapackExtractionGroup {
    val mcfunctions = parseMCFunction(mcf)
    logger.debug { "Parsed ${mcfunctions.size} commands in $path" }
    val extractedArgs = mcfunctions.asSequence().flatMap { command ->
        val selector = extractFromTargetSelector(command.args)

        val command = either {
            extractTextFromCommand(command, mcfPatterns, mcfDataPatterns)
        }.getOrElse {
            logger.error { "Skip $command due to ${it.message}" }
            emptyList()
        }

        selector + command
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

// https://minecraft.wiki/w/Target_selectors
private val SELECTOR_REGEX = Regex("""^@[praesn]\[.*]$""")
private val SELECTOR_NAME_REGEX = Regex("""name=!?("(?:\\.|.)*?"|'.*?'|[\w:]*)[,\]]?""")
private fun extractFromTargetSelector(args: List<MCCommand.Arg>): List<StringIndices> = args.asSequence()
    .filter { SELECTOR_REGEX.matches(it.content) }
    .mapNotNull { arg ->
        SELECTOR_NAME_REGEX.find(arg.content)?.let { result ->
            val negative = result.value.startsWith("name=!")
            val value = if (negative) result.value.removePrefix("name=!") else result.value.removePrefix("name=")
            StringIndices(
                (arg.indices.first + result.range.first + 5 + if (negative) 1 else 0)..<arg.indices.last + result.range.last,
                value
            )
        }
    }
    .toList()


context(_: Raise<IndexSelectError>)
internal fun extractTextFromCommand(
    command: MCCommand,
    mcfPatterns: ExtractPatternSet = BuiltinMCFPatterns,
    mcfDataPatterns: List<DataPointerPattern>? = BuiltinMCFunctionDataPatterns
): List<StringIndices> {
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
        return extractTextFromCommand(subCommand, mcfPatterns)
    }
    return (mcfPatterns[command.name]?.asSequence() ?: emptySequence())
        .filter { it.preCondition.matches(command) }
        .flatMap { pattern ->
            when (val selector = pattern.selected) {
                is IndexSelector.Greedy -> {
                    val commandBeginIndex = command.indices.first
                    val beginIndexRelative =
                        if (selector.position == 0) {
                            if (command.name.length == command.raw.length) command.name.length
                            else command.args.firstOrNull()?.relativeIndices?.first?.minus(command.trimOffset)
                                ?: (command.name.length + command.raw.removePrefix(command.name)
                                    .indexOfFirst { it != ' ' })
                        } else command[selector.position].relativeIndices.first - command.trimOffset
                    val endIndexRelative = command.raw.length - 1
                    val relRange = beginIndexRelative..endIndexRelative
                    val absRange = (commandBeginIndex + command.trimOffset + beginIndexRelative)..
                            (commandBeginIndex + command.trimOffset + endIndexRelative)
                    StringIndices(absRange, command.raw.substring(relRange)).let(::sequenceOf)
                }

                is IndexSelector.NonGreedy ->
                    command.args.asSequence()
                        .withIndex()
                        .filter { (index, _) -> selector.matches(index + 1) }
                        .filter { (_, arg) -> pattern.postCondition.matches(command, arg) }
                        .flatMap { (index, arg) ->
                            val selections =
                                selector.select(index + 1, mcfDataPatterns, arg.content) ?: return@flatMap sequenceOf(
                                    arg
                                )
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
