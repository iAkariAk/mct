package mct.dp.mcfunction

import mct.MCTPattern
import mct.command.extractTextFromCommands
import mct.dp.Extractor
import mct.model.patch.DatapackExtraction.MCFunction
import mct.model.patch.DatapackReplacement
import mct.util.patch


internal fun MCFunctionExtractor(
    patterns: MCTPattern,
) = Extractor("MCFunction", "mcfunction") { sourcePath, (file, tmp) ->
    val (getSource, close) = tmp
    val source = getSource()
    val text = source.readUtf8()
    try {
        extractTextFromCommands(
            commandStr = text,
            patterns = patterns
        ).map { extracted ->
            MCFunction(extracted.indices, extracted.content, extracted.syntax, extracted.format)
        }.toList()
    } finally {
        close(source)
    }
}


internal fun String.backfillMCFunction(replacements: List<DatapackReplacement.MCFunction>): String =
    patch(replacements)
