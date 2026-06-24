package mct.dp.mcjson

import arrow.core.raise.Raise
import arrow.core.raise.context.raise
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import mct.MCTPattern
import mct.dp.Extractor
import mct.dp.MCJsonExtractError
import mct.model.patch.FormatKind
import mct.pointer.*
import mct.text.isTextCompound
import mct.text.isTextCompoundShorthanded
import mct.util.isJson
import mct.util.toJson
import okio.Path
import mct.model.patch.DatapackExtraction.MCJson as MCJsonExtraction

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

context(_: Raise<MCJsonExtractError>)
internal fun extractTextMCJ(
    json: String,
    source: String,
    path: Path,
    patterns: List<DataPointerPattern>? = BuiltinMCJPatterns,
): Sequence<MCJsonExtraction> = try {
    val standard = standardizeMCJson(json)
    val jsonElement = MCJson.decodeFromString<JsonElement>(standard)

    jsonElement.extractTextsByPointer()
        .filterPointer(patterns)
        .map { (pointer, content) ->
            MCJsonExtraction(
                pointer,
                content = content
            )
        }

} catch (e: SerializationException) {
    raise(MCJsonExtractError.JsonSyntaxError(source, path, e))
}

private data class PointerWithExtension(
    val pointer: DataPointer,
    val content: String,
    val kind: FormatKind,
)

private fun Sequence<PointerWithExtension>.filterPointer(patterns: Iterable<DataPointerPattern>?) =
    filter { (ptr, _, _) -> ptr.matches(patterns) }

// coped from NbtTag.PointerWithExtension
private fun JsonElement.extractTextsByPointer(): Sequence<PointerWithExtension> = when (this) {
    is JsonArray -> if (isTextCompound()) {
        sequenceOf(
            PointerWithExtension(
                DataPointer.Terminator,
                toJson(),
                FormatKind.JsonObj,
            )
        )
    } else asSequence().withIndex().flatMap { (index, element) ->
        element.extractTextsByPointer().map {
            it.copy(pointer = it.pointer.markArray(index))
        }
    } // wrap inner pointer

    is JsonObject -> if (isTextCompound()) {
        sequenceOf(PointerWithExtension(DataPointer.Terminator, toJson(), FormatKind.JsonObj))
    } else if (isTextCompoundShorthanded()) {
        val map = toMutableMap()
        val text = map.remove("")
        map["text"] = text!!
        val expanded = JsonObject(map)

        sequenceOf(PointerWithExtension(DataPointer.Terminator, expanded.toJson(), FormatKind.JsonObj))
    } else {
        asSequence().flatMap { (key, value) ->
            value.extractTextsByPointer().map {
                it.copy(pointer = it.pointer.markMap(key))
            }
        } // wrap inner pointer
    }


    is JsonPrimitive if isString -> sequenceOf(
        PointerWithExtension(
            DataPointer.Terminator, content, when {
                content.isJson() -> FormatKind.JsonStr
                else -> FormatKind.PlainStr
            }
        )
    )

    else -> emptySequence()
}

internal fun standardizeMCJson(mcjson: String): String {
    val chars = mcjson.toCharArray()
    val result = StringBuilder(mcjson.length)
    var i = 0
    var inSingleQuote = false
    var inDoubleQuote = false
    while (i < mcjson.length) {
        val c = chars[i]
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

            '\\' if i + 1 < chars.size -> {
                val next = chars[i + 1]
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