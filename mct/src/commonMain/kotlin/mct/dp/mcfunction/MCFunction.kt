package mct.dp.mcfunction

import arrow.core.getOrElse
import arrow.core.raise.context.either
import mct.DatapackExtraction
import mct.DatapackExtractionGroup
import mct.Logger
import mct.command.*
import mct.dp.Extractor
import mct.pointer.DataPointerPattern


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
        val selector = extractTextFromTargetSelector(command.args)

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
            content = extracted.content,
            syntax = extracted.syntax
        )
    }.toList()
    logger.debug { "Extracted ${extractions.size} texts from $path" }
    return DatapackExtractionGroup(
        source = source,
        path = path,
        extractions = extractions
    )
}
