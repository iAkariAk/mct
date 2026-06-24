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
import mct.util.io.endsWith
import mct.util.io.extension
import mct.util.io.walkDirectory
import mct.util.io.walkZip
import net.benwoodworth.knbt.NbtTag
import net.benwoodworth.knbt.decodeFromSource
import net.benwoodworth.knbt.encodeToSink


suspend fun MCTWorkspace.backfillDatapack(replacementGroups: Iterable<DatapackReplacementGroup>) = coroutineScope {
    logger.info { "Backfilling ${replacementGroups.count()} datapack replacement groups" }
    replacementGroups.groupBy {
        datapackDir / it.source
    }.forEach { (datapackPath, replacementGroups) ->
        logger.debug { "Backfilling ${replacementGroups.size} replacements in $datapackPath" }
        launch(Dispatchers.IO) {
            val m = fs.metadata(datapackPath)
            val walk = if (m.isDirectory) fs.walkDirectory(datapackPath) else fs.walkZip(datapackPath)
            val replacementGroups = replacementGroups.associateBy { it.path }
            val writing = walk.write {
                it.path.toString() in replacementGroups
            }
            writing.forEach { (file, tmp1, tmp2, onFailure) ->
                val (getSource, closeSource) = tmp1
                val (getSink, closeSink) = tmp2
                val source = getSource()
                val path = file.path
                val replacementGroup = replacementGroups[path.toString()]!!
                try {
                    @Suppress("UNCHECKED_CAST")
                    when {
                        path.endsWith(".json") -> {
                            val replacements = replacementGroup.replacements as List<DatapackReplacement.MCJson>
                            val origin = source.readUtf8()
                            closeSource(source)
                            val handled = origin.backfillMCJson(replacements)
                            val sink = getSink()
                            sink.writeUtf8(handled)
                            closeSink(sink)
                        }

                        path.endsWith(".mcfunction") -> {
                            val replacements = replacementGroup.replacements as List<DatapackReplacement.MCFunction>
                            val origin = source.readUtf8()
                            closeSource(source)
                            val handled = origin.backfillMCFunction(replacements)
                            val sink = getSink()
                            sink.writeUtf8(handled)
                            closeSink(sink)
                        }


                        path.endsWith(".nbt") -> {
                            val replacements = replacementGroup.replacements as List<DatapackReplacement.Nbt>
                            val origin = runCatching {
                                NbtGzip.decodeFromSource<NbtTag>(source)
                            }.getOrElse {
                                logger.error { "Skip $path because Failed to decode: ${it.message}" }
                                return@forEach
                            }

                            closeSource(source)

                            val ddrg = replacements.map {
                                DataPointerWithValue(it.nbt.pointer, it.replacement, it.nbt.kind)
                            }.toReplacementGroups()
                            val handled = origin.transform(ddrg) ?: origin
                            runCatching {
                                val sink = getSink()
                                NbtNone.encodeToSink(handled, sink)
                                closeSink(sink)
                            }.getOrElse {
                                logger.error { "Skip $path because Failed to encode: ${it.message}" }
                            }
                        }

                        else -> error("Unvalidated extension: ${path.extension}")
                    }
                } catch (e: Throwable) {
                    onFailure(e)
                }
            }
            writing.close()
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
