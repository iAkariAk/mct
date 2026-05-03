package mct.dp.mcjson

import arrow.core.raise.Raise
import arrow.core.raise.context.raise
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import mct.DatapackExtractionGroup
import mct.FormatKind
import mct.dp.Extractor
import mct.dp.MCJsonExtractError
import mct.pointer.*
import mct.DatapackExtraction.MCJson as MCJsonExtraction

internal val MCJson = Json {
    ignoreUnknownKeys = true
    allowTrailingComma = true
    isLenient = true
}

internal fun MCJsonExtractor(
    patterns: List<DataPointerPattern>? = BuiltinMCJPatterns
) = Extractor("MCJson", ".json") { env, zfs, zpath, path ->
    val text = zfs.read(zpath) { readUtf8() }
    extractTextMCJ(
        text,
        source = path.name,
        path = zpath.normalized().toString(),
        patterns
    )
}

context(_: Raise<MCJsonExtractError>)
internal fun extractTextMCJ(
    json: String,
    source: String,
    path: String,
    patterns: List<DataPointerPattern>? = BuiltinMCJPatterns
): DatapackExtractionGroup = try {
    val standard = standardizeMCJson(json)
    val jsonElement = MCJson.decodeFromString<JsonElement>(standard)

    val extractions = jsonElement.extractTextMCJ()
        .filterPointer(patterns)
        .map { (pointer, content) ->
            MCJsonExtraction(
                pointer,
                content = content
            )
        }.toList()
    DatapackExtractionGroup(
        source = source,
        path = path,
        extractions = extractions
    )
} catch (e: SerializationException) {
    raise(MCJsonExtractError.JsonSyntaxError(e))
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

    is JsonPrimitive if isString -> sequenceOf(DataPointerWithValue(DataPointer.Terminator, content, FormatKind.Json))
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
            }

            else -> result.append(c)
        }
        i++
    }
    return result.toString()
}