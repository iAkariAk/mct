package mct.dp.mcjson

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import mct.LoggerHolder
import mct.logger
import mct.model.patch.DatapackReplacement
import mct.model.patch.FormatKind
import mct.model.patch.isString
import mct.pointer.DataPointerReplacementGroup
import mct.pointer.DataPointerWithValue
import mct.pointer.toReplacementGroups
import mct.util.decodeFromString
import mct.util.encodeToString

context(_: LoggerHolder)
internal fun String.backfillMCJson(replacements: List<DatapackReplacement.MCJson>): String {
    val jsonElement = MCJson.decodeFromString<JsonElement>(this)
    val ddrg = replacements.map {
        DataPointerWithValue(it.pointer, it.replacement, it.format)
    }.toReplacementGroups()
    val backfilledJsonElement = jsonElement.transform(ddrg)
    return MCJson.encodeToString(backfilledJsonElement)
}


context(_: LoggerHolder)
private inline fun <reified T> List<DataPointerReplacementGroup>.decodeTerminatorOrNull() =
    firstOrNull { it is DataPointerReplacementGroup.Terminator && it.format == FormatKind.JsonObj }
        ?.let { terminator ->
            terminator as DataPointerReplacementGroup.Terminator
            try {
                MCJson.decodeFromString<T>(terminator.replacement)
            } catch (e: Throwable) {
                logger.error {
                    "Cannot decode ${terminator.replacement} as JSON (${terminator.format}): ${e.message}"
                }
                null
            }
        }


context(_: LoggerHolder)
internal fun JsonElement.transform(pointers: List<DataPointerReplacementGroup>): JsonElement? = when (this) {
    is JsonArray -> {
        pointers.decodeTerminatorOrNull<JsonArray>()?.let {
            return it
        }

        val pointers = pointers.filterIsInstance<DataPointerReplacementGroup.List>()
            .filter { it.point < size }
        if (isEmpty()) return this // safely first to infer type
        val transformed = toMutableList()
        pointers.forEach { pointer ->
            val orig = transformed[pointer.point]
            transformed[pointer.point] = orig.transform(pointer.values) ?: orig
        }
        JsonArray(transformed)
    }

    is JsonObject -> {
        pointers.decodeTerminatorOrNull<JsonObject>()?.let {
            return it
        }

        val pointers = pointers.filterIsInstance<DataPointerReplacementGroup.Map>()
        val transformed = toMutableMap()
        pointers.forEach { pointer ->
            transformed[pointer.point]?.let {
                transformed[pointer.point] = it.transform(pointer.values) ?: it
            }
        }
        JsonObject(transformed)
    }

    is JsonPrimitive if isString -> {
        val pointer =
            pointers.firstOrNull { it is DataPointerReplacementGroup.Terminator && it.format.isString() }
                ?: return this
        pointer as DataPointerReplacementGroup.Terminator
        JsonPrimitive(pointer.replacement)
    }

    else -> this
}

