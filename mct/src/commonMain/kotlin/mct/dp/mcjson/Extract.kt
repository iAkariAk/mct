package mct.dp.mcjson

import arrow.core.raise.Raise
import arrow.core.raise.context.raise
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import mct.dp.MCJsonExtractError
import mct.model.patch.FormatKind
import mct.pointer.*
import mct.text.isTextCompound
import mct.text.isTextCompoundJson
import mct.text.isTextCompoundShorthanded
import mct.util.toJson
import okio.Path
import mct.model.patch.DatapackExtraction.MCJson as MCJsonExtraction


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
        .filter {
            it.pointer.compile().matches(patterns)
        }

} catch (e: SerializationException) {
    raise(MCJsonExtractError.JsonSyntaxError(source, path, e))
}


private typealias PointerWithExtension = MCJsonExtraction // avoid to map object

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
                content.isTextCompoundJson() -> FormatKind.JsonStr
                else -> FormatKind.PlainStr
            }
        )
    )

    else -> emptySequence()
}
