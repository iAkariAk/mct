package mct.dp.mcjson

import arrow.core.raise.Raise
import arrow.core.raise.context.raise
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import mct.LoggerHolder
import mct.MCTPattern
import mct.dp.MCJsonExtractError
import mct.model.patch.ExtractionContent
import mct.model.patch.FormatKind
import mct.model.patch.PointedExtractionContent
import mct.model.patch.inferFormatKind
import mct.model.text.isTextComponent
import mct.model.text.isTextComponentShorthanded
import mct.pointer.*
import mct.util.decodeFromString
import mct.util.toJson
import okio.Path
import org.intellij.lang.annotations.Language
import mct.model.patch.DatapackExtraction.MCJson as MCJsonExtraction


context(_: Raise<MCJsonExtractError>, _: LoggerHolder)
internal fun extractTextFromMCJson(
    @Language("json")
    json: String,
    source: String,
    path: Path,
    pattern: MCTPattern = MCTPattern.Default,
    mcjsonPatterns: List<DataPointerPattern>? = pattern.mcjson
): Sequence<MCJsonExtraction> = try {
    extractTextFromMCJson(json, pattern, mcjsonPatterns).map { MCJsonExtraction(it) }
} catch (e: SerializationException) {
    raise(MCJsonExtractError.JsonSyntaxError(source, path, e))
}

context(_: LoggerHolder)
internal fun extractTextFromMCJson(
    @Language("json")
    json: String,
    pattern: MCTPattern = MCTPattern.Default,
    mcjsonPatterns: List<DataPointerPattern>? = pattern.mcjson
): Sequence<PointedExtractionContent> {
    val jsonElement = MCJson.decodeFromString<JsonElement>(json)
    return jsonElement.extractText(pattern, mcjsonPatterns)
}

context(_: LoggerHolder)
internal fun JsonElement.extractText(
    pattern: MCTPattern = MCTPattern.Default,
    mcjsonPatterns: List<DataPointerPattern>? = pattern.mcjson
): Sequence<PointedExtractionContent> {
    return extractTextsByPointer().mapNotNull { pwe ->
        if (mcjsonPatterns != null) pwe.pointer.compile().matched(mcjsonPatterns)?.let { mcjsonPattern ->
            val content = mcjsonPattern.kind.parse(pwe.content, pwe.format, pattern) ?: return@mapNotNull null
            PointedExtractionContent(pwe.pointer, content)
        } else PointedExtractionContent(pwe.pointer, ExtractionContent.Text(pwe.format, pwe.content))
    }
}

private data class PointerWithExtension(
    val pointer: DataPointer,
    val content: String,
    val format: FormatKind
)

// coped from NbtTag.PointerWithExtension
private fun JsonElement.extractTextsByPointer(): Sequence<PointerWithExtension> = when (this) {
    is JsonArray -> if (isTextComponent()) {
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

    is JsonObject -> if (isTextComponent()) {
        sequenceOf(PointerWithExtension(DataPointer.Terminator, toJson(), FormatKind.JsonObj))
    } else if (isTextComponentShorthanded()) {
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
            DataPointer.Terminator, content, content.inferFormatKind()
        )
    )

    else -> emptySequence()
}
