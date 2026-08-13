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
import mct.model.patch.inferFormatKind
import mct.model.text.isTextComponent
import mct.model.text.isTextComponentShorthanded
import mct.pointer.DataPointer
import mct.pointer.compile
import mct.pointer.markArray
import mct.pointer.markMap
import mct.util.decodeFromString
import mct.util.toJson
import okio.Path
import mct.model.patch.DatapackExtraction.MCJson as MCJsonExtraction


context(_: Raise<MCJsonExtractError>, _: LoggerHolder)
internal fun extractTextMCJ(
    json: String,
    source: String,
    path: Path,
    pattern: MCTPattern = MCTPattern.Default,
): Sequence<MCJsonExtraction> = try {
    val jsonElement = MCJson.decodeFromString<JsonElement>(json)

    val mcjsonPatterns = pattern.mcjson
    jsonElement.extractTextsByPointer().mapNotNull { pwe ->
        if (mcjsonPatterns != null) pwe.pointer.compile().matched(mcjsonPatterns)?.let { mcjsonPattern ->
            val content = mcjsonPattern.kind.parse(pwe.content, pwe.format, pattern) ?: return@mapNotNull null
            MCJsonExtraction(pwe.pointer, content)
        } else MCJsonExtraction(pwe.pointer, ExtractionContent.Text(pwe.format, pwe.content))
    }

} catch (e: SerializationException) {
    raise(MCJsonExtractError.JsonSyntaxError(source, path, e))
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
