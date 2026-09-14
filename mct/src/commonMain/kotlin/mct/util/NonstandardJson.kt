package mct.util

import arrow.core.Either
import arrow.core.left
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.internal.FormatLanguage
import kotlinx.serialization.serializer

typealias EitherJson = Either<Json, NonstandardJson>

fun <T> EitherJson.decodeFromString(
    deserializer: DeserializationStrategy<T>,
    @FormatLanguage("json", prefix = "", suffix = "") string: String
) = fold(
    ifLeft = { it.decodeFromString(deserializer, string) },
    ifRight = { it.decodeFromString(deserializer, string) }
)

fun <T> EitherJson.encodeToString(
    serializer: SerializationStrategy<T>,
    value: T
) = fold(
    ifLeft = { it.encodeToString(serializer, value) },
    ifRight = { it.encodeToString(serializer, value) }
)

val StandardJson = Json {
    ignoreUnknownKeys = true
}

val StandardJsonLeft = StandardJson.left()

data class NonstandardJson(
    val allowTrailingComma: Boolean = true,
    val isLenient: Boolean = false,
    val allowIllegalEscape: Boolean = false,
    val allowSingleQuote: Boolean = false
) {
    private val json = Json {
        val self = this@NonstandardJson
        ignoreUnknownKeys = true
        allowComments = true
        allowTrailingComma = self.allowTrailingComma
        isLenient = self.isLenient
    }

    fun standardize(json: String): String {
        val result = StringBuilder(json.length)
        var i = 0
        var inComments = false
        var inSingleQuote = false
        var inDoubleQuote = false
        while (i < json.length) {
            val c = json[i]
            when (c) {
                '\'' -> if (inDoubleQuote) {
                    result.append(c)
                } else if (inSingleQuote) {
                    inSingleQuote = false
                    if (allowSingleQuote) result.append('"')
                    else result.append(c)
                } else {
                    inSingleQuote = true
                    result.append('"')
                }


                '"' -> if (inSingleQuote) {
                    if (allowSingleQuote) result.append("\\\"")
                    else result.append(c)
                } else if (inDoubleQuote) {
                    inDoubleQuote = false
                    result.append(c)
                } else {
                    inDoubleQuote = true
                    result.append(c)
                }

                '\\' if i + 1 < json.length -> {
                    val next = json[i + 1]
                    when {
                        inSingleQuote || inDoubleQuote -> {
                            when (next) {
                                '\'' if (allowIllegalEscape || allowSingleQuote) -> result.append("'") // unescape
                                '\\' if inSingleQuote -> result.append("\\\\")
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

                '/' if i + 1 < json.length -> {
                    val next = json[i + 1]
                    when (next) {
                        '/' -> {
                            val j = json.indexOf('\n', i + 2)
                            if (j == -1) break
                            i = j

                        }

                        '*' -> {
                            val j = json.indexOf("*/", i + 2)
                            if (j == -1) break
                            i = j

                        }
                    }
                }

                else -> result.append(c)
            }
            i++
        }
        return result.toString()
    }

    fun <T> decodeFromString(
        deserializer: DeserializationStrategy<T>,
        @FormatLanguage("json", prefix = "", suffix = "") string: String
    ) = json.decodeFromString(deserializer, standardize(string))

    fun <T> encodeToString(
        serializer: SerializationStrategy<T>,
        value: T
    ) = json.encodeToString(serializer, value)
}

inline fun <reified T> NonstandardJson.decodeFromString(
    @FormatLanguage(
        "json",
        prefix = "",
        suffix = ""
    ) string: String
): T = decodeFromString(serializer<T>(), string)

inline fun <reified T> NonstandardJson.encodeToString(
    value: T
) = encodeToString(serializer<T>(), value)
