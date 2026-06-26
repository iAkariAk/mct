package mct.dp.mcfunction

import mct.MCTPattern
import mct.command.extractTextFromCommands
import mct.dp.Extractor
import mct.model.patch.DatapackExtraction.MCFunction


internal fun MCFunctionExtractor(
    pattern: MCTPattern,
) = Extractor("MCFunction", "mcfunction") { sourcePath, (file, tmp) ->
    val (getSource, close) = tmp
    val source = getSource()
    val text = source.readUtf8()
    try {
        extractTextFromCommands(
            commandStr = text,
            commandPatterns = pattern.command,
            commandDataPatterns = pattern.commandData,
            commandRegexPatterns = pattern.commandRegex
        ).map { extracted ->
            MCFunction(indices = extracted.indices, content = extracted.content, syntax = extracted.syntax)
        }.toList()
    } finally {
        close(source)
    }
}


