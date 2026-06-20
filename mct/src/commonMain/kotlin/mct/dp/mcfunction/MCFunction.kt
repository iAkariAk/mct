package mct.dp.mcfunction

import mct.MCTPattern
import mct.command.extractTextFromCommands
import mct.dp.Extractor
import mct.logger
import mct.model.patch.DatapackExtraction.MCFunction


internal fun MCFunctionExtractor(
    pattern: MCTPattern,
) = Extractor("MCFunction", "mcfunction") { sourcePath, zfs, zpath ->
    val text = zfs.read(zpath) { readUtf8() }
    val extractions = extractTextFromCommands(
        commandStr = text,
        mcfPatterns = pattern.mcfunction,
        mcfDataPatterns = pattern.mcfunctionData,
        regexPatterns = pattern.mcfunctionRegex
    ).map { extracted ->
        MCFunction(indices = extracted.indices, content = extracted.content, syntax = extracted.syntax)
    }.toList()
    logger.debug { "Extracted ${extractions.size} texts from ${zpath.normalized().toString()}" }
    extractions
}


