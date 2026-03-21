package mct.region

import arrow.core.raise.Raise
import kotlinx.coroutines.flow.*
import mct.MCTWorkspace
import mct.RegionExtraction
import mct.RegionExtractionGroup
import mct.pointer.*
import mct.region.anvil.Coord
import mct.region.anvil.model.ChunkDataKind
import net.benwoodworth.knbt.NbtCompound
import net.benwoodworth.knbt.NbtList
import net.benwoodworth.knbt.NbtString
import net.benwoodworth.knbt.NbtTag


context(_: Raise<ExtractError>)
fun MCTWorkspace.extractFromRegion(
    patterns: Set<DataPointerPattern> = BuiltinPatterns
): Flow<RegionExtractionGroup> =
    dimensions.values.asFlow().flatMapMerge { dimension ->
        flowOf(
            dimension.regionRawMgr to ChunkDataKind.Terrain,
            dimension.poiRawMgr to ChunkDataKind.Poi,
            dimension.entitiesRawMgr to ChunkDataKind.Entities,
        )
            .filter { (manager, _) -> manager != null }
            .flatMapMerge { (manager, kind) ->
                manager!!.regions().asFlow().flatMapMerge(4) { region ->
                    val extractions = region.chunks.asSequence()
                        .filterNotNull()
                        .flatMap { chunk ->
                            chunk.data.extractTexts()
                                .filterPointer(patterns)
                                .map { (pointer, content) ->
                                    RegionExtraction(
                                        index = chunk.index.toInt(),
                                        pointer = pointer,
                                        content = content
                                    )

                                }
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
    }


internal fun NbtTag.extractTexts(): Sequence<DataPointerWithValue> = when (this) {
    is NbtList<*> -> asSequence().withIndex().flatMap { (index, tag) ->
        tag.extractTexts().map {
            it.markArray(index)
        }
    } // wrap inner pointer

    is NbtCompound -> asSequence().flatMap { (key, value) ->
        value.extractTexts().map {
            it.markMap(key)
        }
    } // wrap inner pointer

    is NbtString -> sequenceOf(DataPointer.Terminator to value)
    else -> emptySequence()
}


