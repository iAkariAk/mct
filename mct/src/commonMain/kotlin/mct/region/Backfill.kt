package mct.region

import arrow.core.raise.Raise
import arrow.core.raise.context.raise
import arrow.core.raise.recover
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import mct.MCTWorkspace
import mct.model.patch.RegionReplacementGroup
import mct.nbt.transform
import mct.pointer.DataPointerWithValue
import mct.pointer.toReplacementGroups
import mct.region.anvil.model.ChunkDataKind
import mct.util.IO

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
                            val ddrg = replacements.map {
                                DataPointerWithValue(it.nbt.pointer, it.replacement, it.nbt.kind)
                            }.toReplacementGroups()
                            val chunk = chunks[index] ?: return@forEach
                            chunks[index] = chunk.modify {
                                it.transform(ddrg) ?: it
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


