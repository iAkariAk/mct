package mct.dp

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import mct.DatapackReplacement
import mct.DatapackReplacementGroup
import mct.MCTWorkspace
import mct.dp.mcjson.MCJson
import mct.dp.mcjson.standardizeMCJson
import mct.pointer.DataPointer
import mct.pointer.DataPointerReplacementGroup
import mct.pointer.toReplacementGroups
import mct.util.io.newRelativeFS
import mct.util.io.openZipReadWrite
import mct.util.io.useAsync
import mct.util.io.writeText
import okio.BufferedSource
import okio.Path.Companion.toPath
import kotlin.jvm.JvmName


suspend fun MCTWorkspace.backfillDatapack(replacementGroups: Iterable<DatapackReplacementGroup>) = coroutineScope {
    replacementGroups.groupBy {
        datapackDir / it.source
    }.forEach { (dbPath, replacementGroups) ->
        launch {
            val m = fs.metadata(dbPath)
            val sfs = if (m.isDirectory) fs.newRelativeFS(dbPath) else fs.openZipReadWrite(dbPath)
            sfs.useAsync { sfs ->
                replacementGroups.forEach { replacementGroup ->
                    val path = replacementGroup.path.toPath()
                    val origin = sfs.read(path, BufferedSource::readUtf8)
                    val handled = origin.backfill(replacementGroup.replacements)
                    path.writeText(handled, sfs)
                }
            }
        }
    }
}

internal fun String.backfill(replacements: List<DatapackReplacement>): String {
    val mcfunction = mutableListOf<DatapackReplacement.MCFunction>()
    val mcjson = mutableListOf<DatapackReplacement.MCJson>()
    replacements.forEach { replacement ->
        when (replacement) {
            is DatapackReplacement.MCFunction -> mcfunction.add(replacement)
            is DatapackReplacement.MCJson -> mcjson.add(replacement)
        }
    }
    if (mcfunction.isNotEmpty()) return backfill(mcfunction)
    if (mcjson.isNotEmpty()) return backfill(mcjson)
    return this
}

@JvmName($$"backfill$MCFunction")
internal fun String.backfill(replacements: List<DatapackReplacement.MCFunction>) =
    replacements
        .sortedByDescending { it.indices.first }
        .fold(StringBuilder(this)) { ace, e ->
            ace.replaceRange(e.indices, e.replacement) as StringBuilder
        }.toString()

@JvmName($$"backfill$MCJson")
internal fun String.backfill(replacements: List<DatapackReplacement.MCJson>): String {
    val standardizedJson = standardizeMCJson(this)
    val jsonElement = MCJson.decodeFromString<JsonElement>(standardizedJson)
    val pointerDatapackReplacementGroups = replacements.map { it.pointer to it.replacement }.toReplacementGroups()
    val backfilledJsonElement = jsonElement.transform(pointerDatapackReplacementGroups)
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
