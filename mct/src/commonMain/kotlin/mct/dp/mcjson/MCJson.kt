package mct.dp.mcjson

import kotlinx.serialization.json.Json
import mct.MCTPattern
import mct.dp.Extractor

internal val MCJson = Json {
    ignoreUnknownKeys = true
    allowTrailingComma = true
    isLenient = true
}

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

internal fun standardizeMCJson(mcjson: String): String {
    val result = StringBuilder(mcjson.length)
    var i = 0
    var inSingleQuote = false
    var inDoubleQuote = false
    while (i < mcjson.length) {
        val c = mcjson[i]
        when (c) {
            '\'' -> if (inDoubleQuote) {
                result.append(c)
            } else if (inSingleQuote) {
                inSingleQuote = false
                result.append('"')
            } else {
                inSingleQuote = true
                result.append('"')
            }


            '"' -> if (inSingleQuote) {
                result.append("\\\"")
            } else if (inDoubleQuote) {
                inDoubleQuote = false
                result.append(c)
            } else {
                inDoubleQuote = true
                result.append(c)
            }

            '\\' if i + 1 < mcjson.length  -> {
                val next = mcjson[i + 1]
                when {
                    inSingleQuote -> {
                        when (next) {
                            '\'' -> result.append("'") // unescape
                            '\\' -> result.append("\\\\")
                            else -> {
                                result.append('\\')
                                result.append(next)
                            }
                        }
                        i++
                    }

                    inDoubleQuote -> {
                        result.append('\\')
                        result.append(next)
                        i++
                    }

                    else -> result.append(c)
                }
            }

            else -> result.append(c)
        }
        i++
    }
    return result.toString()
}
