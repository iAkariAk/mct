package mct.region

import arrow.core.raise.Raise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.*
import mct.FormatKind
import mct.MCTWorkspace
import mct.RegionExtraction
import mct.RegionExtractionGroup
import mct.pointer.*
import mct.region.anvil.Coord
import mct.region.anvil.model.ChunkDataKind
import mct.text.isTextCompound
import mct.util.toSnbt
import net.benwoodworth.knbt.NbtCompound
import net.benwoodworth.knbt.NbtList
import net.benwoodworth.knbt.NbtString
import net.benwoodworth.knbt.NbtTag


context(_: Raise<ExtractError>)
fun MCTWorkspace.extractFromRegion(
    patterns: List<DataPointerPattern>? = BuiltinRegionPatterns
): Flow<RegionExtractionGroup> {
    if (patterns == null) logger.warning { "The filter was disabled, which causes export all string from the region" }
    logger.info { "Extracting from ${dimensions.size} dimensions" }

    return dimensions.values.asFlow().flatMapMerge { dimension ->
        flowOf(
            dimension.regionRawMgr to ChunkDataKind.Terrain,
            dimension.poiRawMgr to ChunkDataKind.Poi,
            dimension.entitiesRawMgr to ChunkDataKind.Entities
        )
            .filter { (manager, _) -> manager != null }
            .flatMapMerge { (manager, kind) ->
                manager!!.regions().asFlow().flatMapMerge(4) { region ->
                    val extractions = region.chunks.asSequence()
                        .filterNotNull()
                        .flatMap { chunk ->
                            chunk.data.extractTexts()
                                .filterPointer(patterns)
                                .map { (pointer, content, kind) ->
                                    RegionExtraction(
                                        index = chunk.index,
                                        pointer = pointer,
                                        kind = kind,
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
                }.flowOn(Dispatchers.IO)
            }
            .flowOn(Dispatchers.IO)
    }.flowOn(Dispatchers.IO)
}

internal data class PointerWithExtension(
    val pointer: DataPointer,
    val content: String,
    val kind: FormatKind = FormatKind.Json,
)

private inline fun Sequence<PointerWithExtension>.filterPointer(patterns: Iterable<DataPointerPattern>?) =
    filter { (ptr, _, _) -> ptr.matches(patterns) }

internal fun NbtTag.extractTexts(): Sequence<PointerWithExtension> = when (this) {
    is NbtList<*> -> asSequence().withIndex().flatMap { (index, tag) ->
        tag.extractTexts().map {
            it.copy(pointer = it.pointer.markArray(index))
        }
    } // wrap inner pointer

    is NbtCompound -> {
        if (isTextCompound()) {
            sequenceOf(PointerWithExtension(DataPointer.Terminator, toSnbt(), FormatKind.Snbt))
        } else
            asSequence().flatMap { (key, value) ->
                value.extractTexts().map {
                    it.copy(pointer = it.pointer.markMap(key))
                }
            } // wrap inner pointer
    }

    is NbtString -> {
        sequenceOf(PointerWithExtension(DataPointer.Terminator, value))
    }

    else -> emptySequence()
}

