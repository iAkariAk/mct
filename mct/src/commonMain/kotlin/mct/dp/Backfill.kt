package mct.dp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import mct.MCTWorkspace
import mct.dp.mcjson.MCJson
import mct.dp.mcjson.standardizeMCJson
import mct.model.patch.DatapackReplacement
import mct.model.patch.DatapackReplacementGroup
import mct.model.patch.FormatKind
import mct.nbt.transform
import mct.pointer.DataPointer
import mct.pointer.DataPointerReplacementGroup
import mct.pointer.DataPointerWithValue
import mct.pointer.toReplacementGroups
import mct.serializer.NbtGzip
import mct.serializer.NbtNone
import mct.util.IO
import mct.util.io.*
import net.benwoodworth.knbt.NbtTag
import net.benwoodworth.knbt.decodeFromSource
import net.benwoodworth.knbt.encodeToSink
import okio.Path.Companion.toPath


suspend fun MCTWorkspace.backfillDatapack(replacementGroups: Iterable<DatapackReplacementGroup>) = coroutineScope {
    logger.info { "Backfilling ${replacementGroups.count()} datapack replacement groups" }
    replacementGroups.groupBy {
        datapackDir / it.source
    }.forEach { (dbPath, replacementGroups) ->
        logger.debug { "Backfilling ${replacementGroups.size} replacements in $dbPath" }

        launch(Dispatchers.IO) {
            val m = fs.metadata(dbPath)
            val sfs = if (m.isDirectory) fs.newRelativeFS(dbPath) else fs.openZipReadWrite(dbPath)
            sfs.useAsync { sfs ->
                replacementGroups.forEach { replacementGroup ->
                    val path = replacementGroup.path.toPath()

                    @Suppress("UNCHECKED_CAST")
                    when {
                        path.endsWith(".json") -> {
                            val replacements = replacementGroup.replacements as List<DatapackReplacement.MCJson>
                            val origin = path.readText(sfs)
                            val handled = origin.backfillMCJson(replacements)
                            path.writeText(handled, sfs)
                        }

                        path.endsWith(".mcfunction") -> {
                            val replacements = replacementGroup.replacements as List<DatapackReplacement.MCFunction>
                            val origin = path.readText(sfs)
                            val handled = origin.backfillMCFunction(replacements)
                            path.writeText(handled, sfs)
                        }


                        path.endsWith(".nbt") -> {
                            val replacements = replacementGroup.replacements as List<DatapackReplacement.Nbt>
                            val origin = sfs.read(path) {
                                runCatching {
                                    NbtGzip.decodeFromSource<NbtTag>(this)
                                }.getOrElse {
                                    logger.error { "Skip $path because Failed to decode: ${it.message}" }
                                    return@forEach
                                }
                            }
                            val ddrg = replacements.map {
                                DataPointerWithValue(it.nbt.pointer, it.replacement, it.nbt.kind)
                            }.toReplacementGroups()
                            val handled = origin.transform(ddrg) ?: origin
                            sfs.write(path) {
                                NbtNone.encodeToSink(handled, this)
                            }
                        }

                        else -> error("Unvalidated extension: ${path.extension}")
                    }
                }
            }
        }
    }
}


internal fun String.backfillMCFunction(replacements: List<DatapackReplacement.MCFunction>) =
    replacements
        .sortedByDescending { it.indices.first }
        .fold(StringBuilder(this)) { acc, e ->
            acc.setRange(e.indices.first, e.indices.last + 1, e.replacement)
        }.toString()

internal fun String.backfillMCJson(replacements: List<DatapackReplacement.MCJson>): String {
    val standardizedJson = standardizeMCJson(this)
    val jsonElement = MCJson.decodeFromString<JsonElement>(standardizedJson)
    val pointerDatapackReplacementGroups =
        replacements.map { DataPointerWithValue(it.pointer, it.replacement, FormatKind.JsonStr) }.toReplacementGroups()
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
