package mct.region

import arrow.core.raise.Raise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import mct.MCTPattern
import mct.MCTWorkspace
import mct.model.patch.RegionExtraction
import mct.model.patch.RegionExtractionGroup
import mct.nbt.extractTexts
import mct.region.anvil.Coord
import mct.region.anvil.model.ChunkDataKind
import mct.util.IO


context(_: Raise<ExtractError>)
fun MCTWorkspace.extractFromRegion(
    pattern: MCTPattern = MCTPattern.Default,
): Flow<RegionExtractionGroup> {
    logger.info { "Extracting from ${dimensions.size} dimensions" }

    return dimensions.values.asFlow().flatMapMerge { dimension ->
        flowOf(
            dimension.regionRawMgr to ChunkDataKind.Terrain,
            dimension.poiRawMgr to ChunkDataKind.Poi,
            dimension.entitiesRawMgr to ChunkDataKind.Entities
        )
            .filter { (manager, _) -> manager != null }
            .flatMapMerge { (manager, kind) ->
                manager!!.regions().asFlow().flatMapMerge { region ->
                    val extractions = region.chunks.asSequence()
                        .filterNotNull()
                        .flatMap { chunk ->
                            chunk.data.fold(
                                ifLeft = {
                                    logger.error { "Cannot decode data from $dimension/$kind/${region.inferFilename()}: ${it.message}" }
                                    emptySequence()
                                },
                                ifRight = { data ->
                                    data.extractTexts(pattern).map {
                                        RegionExtraction(index = chunk.index, nbt = it)
                                    }
                                }
                            )
                        }
                    flowOf(
                        RegionExtractionGroup(
                            dimension = dimension.id,
                            kind = kind,
                            coord = Coord(region.regionX, region.regionZ),
                            extractions = extractions.toList().takeIf { it.isNotEmpty() }
                                ?: return@flatMapMerge emptyFlow()
                        )
                    )
                }
            }
    }.flowOn(Dispatchers.IO.limitedParallelism(72))
}
