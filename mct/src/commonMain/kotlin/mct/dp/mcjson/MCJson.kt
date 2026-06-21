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
import mct.util.isJson
import mct.model.patch.DatapackExtraction.MCJson as MCJsonExtraction

internal val MCJson = Json {
    ignoreUnknownKeys = true
    allowTrailingComma = true
    isLenient = true
}

internal fun MCJsonExtractor(
    pattern: MCTPattern,
) = Extractor("MCJson", "json") { sourcePath, zfs, zpath ->
    val text = zfs.read(zpath) { readUtf8() }
    extractTextMCJ(
        text,
        source = sourcePath.name,
        path = zpath.normalized().toString(),
        pattern.mcjson
    ).toList()
}

context(_: Raise<MCJsonExtractError>)
internal fun extractTextMCJ(
    json: String,
    source: String,
    path: String,
    patterns: List<DataPointerPattern>? = BuiltinMCJPatterns
): Sequence<MCJsonExtraction> = try {
    val standard = standardizeMCJson(json)
    val jsonElement = MCJson.decodeFromString<JsonElement>(standard)

    jsonElement.extractTextMCJ()
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

internal fun JsonElement.extractTextMCJ(): Sequence<DataPointerWithValue> = when (this) {
    is JsonArray -> asSequence().withIndex().flatMap { (index, element) ->
        element.extractTextMCJ().map { it.markArray(index) }
    }

    is JsonObject -> asSequence().flatMap { (key, value) ->
        value.extractTextMCJ().map {
            it.markMap(key)
        }
    }

    is JsonPrimitive if isString -> sequenceOf(
        DataPointerWithValue(
            DataPointer.Terminator, content, when {
                content.isJson() -> FormatKind.JsonStr
                else -> FormatKind.PlainStr
            }
        )
    )

    JsonNull -> emptySequence()
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
            '\'' if !inDoubleQuote -> {
                inSingleQuote = !inSingleQuote
                result.append('"')
            }

            '"' -> when {
                inSingleQuote -> result.append("\\\"")
                inDoubleQuote -> {
                    result.append("\"")
                    inDoubleQuote = !inDoubleQuote
                }

                else -> result.append("\"")
            }

            '\\' if inSingleQuote && i + 1 < mcjson.length && chars[i + 1] == '\'' -> {
                result.append('\'')
                i++
            }

            else -> result.append(c)
        }
        i++
    }
    return result.toString()
}