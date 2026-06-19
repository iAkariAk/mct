package mct.dp.mcfunction

import arrow.core.getOrElse
import arrow.core.raise.context.either
import mct.LoggerHolder
import mct.MCTPattern
import mct.command.*
import mct.dp.Extractor
import mct.logger
import mct.model.patch.DatapackExtraction
import mct.pointer.DataPointerPattern


internal fun MCFunctionExtractor(
    pattern: MCTPattern,
) = Extractor("MCFunction", "mcfunction") { sourcePath, zfs, zpath ->
    val text = zfs.read(zpath) { readUtf8() }
    extractTextMCF(
        text,
        source = sourcePath.name,
        path = zpath.normalized().toString(),
        mcfPatterns = pattern.mcfunction,
        mcfDataPatterns = pattern.mcfunctionData
    )

}


context(_: LoggerHolder)
internal fun extractTextMCF(
    mcf: String,
    source: String,
    path: String,
    mcfPatterns: ExtractPatternSet = BuiltinMCFPatterns,
    mcfDataPatterns: List<DataPointerPattern>? = BuiltinMCFunctionDataPatterns
): List<DatapackExtraction> {
    val mcfunctions = parseCommands(mcf)
    logger.debug { "Parsed ${mcfunctions.size} commands in $path ($source)" }
    val extractedArgs = mcfunctions.asSequence().flatMap { command ->
        either {
            extractTextFromCommand(command, mcfPatterns, mcfDataPatterns)
        }.getOrElse {
            logger.error { "Skip $command due to ${it.message}" }
            emptyList()
        }
    }
    val extractions = extractedArgs.map { extracted ->
        DatapackExtraction.MCFunction(
            indices = extracted.indices, content = extracted.content, syntax = extracted.syntax
        )
    }.toList()
    logger.debug { "Extracted ${extractions.size} texts from $path" }
    return extractions
}
