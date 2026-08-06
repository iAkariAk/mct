package mct.dp.mcjson

import mct.MCTPattern
import mct.dp.Extractor

internal fun MCJsonExtractor(
    pattern: MCTPattern,
) = Extractor("MCJson", "json") { sourcePath, (file, tmp) ->
    val (getSource, close) = tmp
    val source = getSource()
    val text = source.readUtf8()
    try {
        extractTextMCJ(
            text,
            source = sourcePath.name,
            path = file.path,
            pattern.mcjson
        ).toList()
    } finally {
        close(source)
    }
}
