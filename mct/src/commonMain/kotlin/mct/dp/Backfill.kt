package mct.dp

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import mct.MCTWorkspace
import mct.dp.mcjson.MCJson
import mct.dp.mcjson.standardizeMCJson
import mct.pointer.DataPointer
import mct.pointer.DataPointerReplacementGroup
import mct.pointer.toReplacementGroups
import mct.util.io.openZipReadWrite
import mct.util.io.use2
import mct.util.io.writeText
import okio.BufferedSource
import okio.Path.Companion.toPath
import kotlin.jvm.JvmName
import mct.Replacement.Datapack as Replacement
import mct.ReplacementGroup.Datapack as ReplacementGroup


suspend fun MCTWorkspace.backfillDatapack(replacementGroups: Iterable<ReplacementGroup>) = coroutineScope {
    replacementGroups.groupBy {
        datapackDir / it.source
    }.forEach { (dbPath, replacementGroup) ->
        launch {
            fs.openZipReadWrite(dbPath).use2 { zfs ->
                replacementGroup.forEach { replacementGroup ->
                    val path = replacementGroup.path.toPath()
                    val origin = zfs.read(path, BufferedSource::readUtf8)
                    val handled = origin.backfill(replacementGroup.replacements)
                    path.writeText(handled, env.fs)
                }
            }
        }
    }
}

internal fun String.backfill(replacements: List<Replacement>): String {
    val mcfunction = mutableListOf<Replacement.MCFunction>()
    val mcjson = mutableListOf<Replacement.MCJson>()
    replacements.forEach { replacement ->
        when (replacement) {
            is Replacement.MCFunction -> mcfunction.add(replacement)
            is Replacement.MCJson -> mcjson.add(replacement)
        }
    }
    if (mcfunction.isNotEmpty()) return backfill(mcfunction)
    if (mcjson.isNotEmpty()) return backfill(mcjson)
    return this
}

@JvmName($$"backfill$MCJson")
internal fun String.backfill(replacements: List<Replacement.MCJson>): String {
    val standardizedJson = standardizeMCJson(this)
    val jsonElement = MCJson.decodeFromString<JsonElement>(standardizedJson)
    val pointerReplacementGroups = replacements.map { it.pointer to it.replacement }.toReplacementGroups()
    val backfilledJsonElement = jsonElement.transform(pointerReplacementGroups)
    return MCJson.encodeToString(backfilledJsonElement)
}


private fun JsonElement.transform(pointer: DataPointer, replacement: String): JsonElement = when (pointer) {
    is DataPointer.List -> {
        if (this !is JsonArray) return this
        if (size <= pointer.point) return this
        val transformed = toMutableList()
        transformed[pointer.point] = transformed[pointer.point].transform(pointer.value, replacement)
        JsonArray(transformed)
    }

    is DataPointer.Map -> {
        if (this !is JsonObject) return this
        if (!containsKey(pointer.point)) return this
        val transformed = toMutableMap()
        transformed[pointer.point] = transformed[pointer.point]!!.transform(pointer.value, replacement)
        JsonObject(transformed)
    }

    DataPointer.Terminator -> {
        return JsonPrimitive(replacement)
    }
}

private fun JsonElement.transform(
    pointers: List<DataPointerReplacementGroup>,
): JsonElement = when (this) {
    is JsonArray -> {
        val pointers = pointers.filterIsInstance<DataPointerReplacementGroup.List>()
            .filter { it.point < size }
        if (isEmpty()) return this // safely first to infer type
        val transformed = toMutableList()
        pointers.forEach { pointer ->
            transformed[pointer.point] = transformed[pointer.point].transform(pointer.values)
        }
        JsonArray(transformed)
    }

    is JsonObject -> {
        val pointers = pointers.filterIsInstance<DataPointerReplacementGroup.Map>()

        val transformed = toMutableMap()
        pointers.forEach { pointer ->
            transformed[pointer.point]?.let {
                transformed[pointer.point] = it.transform(pointer.values)
            }
        }
        JsonObject(transformed)
    }

    is JsonPrimitive if isString -> {
        val pointers = pointers.filterIsInstance<DataPointerReplacementGroup.Terminator>()

        val pointer = pointers.firstOrNull() ?: return this

        JsonPrimitive(pointer.replacement)
    }

    else -> this
}
