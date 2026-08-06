package mct.util

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.internal.FormatLanguage
import kotlinx.serialization.serializer
import mct.serializer.MCTJson

inline fun <reified T> decodeFromMCJson(@FormatLanguage("json", prefix = "", suffix = "") string: String): T =
    decodeFromMCJson(serializer<T>(), string)

fun <T> decodeFromMCJson(
    deserializer: DeserializationStrategy<T>,
    @FormatLanguage("json", prefix = "", suffix = "") string: String
): T = MCTJson.decodeFromString(deserializer, standardizeMCJson(string))

internal val MCJson = Json {
    ignoreUnknownKeys = true
    allowTrailingComma = true
    isLenient = true
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

            '\\' if i + 1 < mcjson.length -> {
                val next = mcjson[i + 1]
                when {
                    inSingleQuote || inDoubleQuote -> {
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

                    else -> result.append(c)
                }
            }

            else -> result.append(c)
        }
        i++
    }
    return result.toString()
}
