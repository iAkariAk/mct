package mct.region

import arrow.core.raise.Raise
import arrow.core.raise.context.raise
import arrow.core.raise.recover
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import mct.FormatKind
import mct.MCTWorkspace
import mct.RegionReplacementGroup
import mct.pointer.DataPointerReplacementGroup
import mct.pointer.DataPointerWithValue
import mct.pointer.toReplacementGroups
import mct.region.anvil.model.ChunkDataKind
import mct.serializer.Snbt
import mct.text.TextCompound
import mct.text.encodeToIR
import mct.util.IO
import mct.util.formatir.toNbt
import net.benwoodworth.knbt.NbtCompound
import net.benwoodworth.knbt.NbtList
import net.benwoodworth.knbt.NbtString
import net.benwoodworth.knbt.NbtTag

context(_: Raise<BackfillError>)
suspend fun MCTWorkspace.backfillRegion(replacementGroups: Iterable<RegionReplacementGroup>) = coroutineScope {
    logger.info { "Backfilling ${replacementGroups.count()} region replacement groups" }
    replacementGroups.forEach { group ->
        val dimension = dimensions[group.dimension]
            ?: raise(BackfillError.DimensionNotFound(group.dimension))
        val mgr = when (group.kind) {
            ChunkDataKind.Terrain -> dimension.regionRawMgr
            ChunkDataKind.Entities -> dimension.entitiesRawMgr
            ChunkDataKind.Poi -> dimension.poiRawMgr
        }
        if (mgr == null) {
            logger.debug { "Skip ${group.dimension}/${group.kind}: manager unavailable" }
            return@forEach
        }
        logger.debug { "Backfilling ${group.dimension}/${group.kind} at (${group.coord.x}, ${group.coord.z}) with ${group.replacements.size} replacements" }
        launch(Dispatchers.IO) {
            recover({
                mgr.modify(group.coord) { region ->
                    val chunks = region.chunks.toMutableList()
                    group.replacements.groupBy { it.index }
                        .forEach { (index, replacements) ->
                            val replacementGroups =
                                replacements.map {
                                    DataPointerWithValue(it.pointer, it.replacement, it.kind)
                                }
                                    .toReplacementGroups()
                            val chunk = chunks[index] ?: return@forEach
                            chunks[index] = chunk.modify {
                                it.transform(replacementGroups)
                            }
                        }
                    region.modifyChunks(chunks)
                }
            }, {
                raise(BackfillError.Internal(it))
            })
        }
    }
}

private fun List<NbtTag>.toTCListStandardized() = map {
    when (it) {
        is NbtString -> TextCompound.Plain(it.value).encodeToIR(false).toNbt() as NbtCompound
        is NbtCompound -> it
        else -> error("Unexpected tag type $it")
    }
}.let { NbtList(it) }

private fun NbtTag.transform(
    pointers: List<DataPointerReplacementGroup>,
): NbtTag = when (this) {
    is NbtList<*> -> {
        val pointers = pointers.filterIsInstance<DataPointerReplacementGroup.List>()
            .filter { it.point < size }
        if (isEmpty()) return this // safely first to infer type
        val transformed = toMutableList()
        pointers.forEach { pointer ->
            transformed[pointer.point] = transformed[pointer.point].transform(pointer.values)
        }
        transformed.toTCListStandardized()
    }

    is NbtCompound -> {
        pointers.firstOrNull { it is DataPointerReplacementGroup.Terminator && it.kind == FormatKind.Nbt }
            ?.let { terminator ->
                terminator as DataPointerReplacementGroup.Terminator

                return Snbt.decodeFromString(terminator.replacement)
            }

        val pointers = pointers.filterIsInstance<DataPointerReplacementGroup.Map>()
        val transformed = toMutableMap()
        pointers.forEach { pointer ->
            transformed[pointer.point]?.let {
                transformed[pointer.point] = it.transform(pointer.values)
            }
        }
        NbtCompound(transformed)
    }

    is NbtString -> {
        val pointer = pointers.firstOrNull { it is DataPointerReplacementGroup.Terminator && it.kind == FormatKind.Str } ?: return this
        pointer as DataPointerReplacementGroup.Terminator

        NbtString(pointer.replacement)
    }

    else -> this
}


