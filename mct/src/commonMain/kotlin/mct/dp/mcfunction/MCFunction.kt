package mct.dp.mcfunction

import mct.MCTPattern
import mct.command.extractTextFromCommands
import mct.dp.Extractor
import mct.model.patch.DatapackExtraction.MCFunction
import mct.model.patch.DatapackReplacement


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
            MCFunction(indices = extracted.indices, content = extracted.content, syntax = extracted.syntax)
        }.toList()
    } finally {
        close(source)
    }
}


internal fun String.backfillMCFunction(replacements: List<DatapackReplacement.MCFunction>): String {
    val sortedByDescending = replacements
        .sortedByDescending { it.indices.first }
    for (i in sortedByDescending.indices.reversed()) {
        val current = sortedByDescending[i]
        val next = sortedByDescending.getOrNull(i - 1) ?: break
        require (current.indices.last < next.indices.first) {
            "Replacements cannot overlap with each other ($current and $next)"
        }
    }
    return sortedByDescending
        .fold(StringBuilder(this)) { acc, e ->
            acc.setRange(e.indices.first, e.indices.last + 1, e.replacement)
        }.toString()
}
