package mct.region

import arrow.core.raise.Raise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.*
import mct.MCTWorkspace
import mct.RegionExtraction
import mct.RegionExtractionGroup
import mct.pointer.*
import mct.region.anvil.Coord
import mct.region.anvil.model.ChunkDataKind
import mct.util.isTextCompound
import mct.util.toSnbt
import net.benwoodworth.knbt.NbtCompound
import net.benwoodworth.knbt.NbtList
import net.benwoodworth.knbt.NbtString
import net.benwoodworth.knbt.NbtTag


context(_: Raise<ExtractError>)
fun MCTWorkspace.extractFromRegion(
    patterns: Set<DataPointerPattern> = emptySet()
): Flow<RegionExtractionGroup> {
    val patterns = BuiltinPatterns + patterns
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
                                .map { (pointer, content, isStoredViaCompound) ->
                                    RegionExtraction(
                                        index = chunk.index.toInt(),
                                        pointer = pointer,
                                        isStoredViaCompound = isStoredViaCompound,
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
    val isStoredViaCompound: Boolean,
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
            sequenceOf(PointerWithExtension(DataPointer.Terminator, toSnbt(), true))
        } else
            asSequence().flatMap { (key, value) ->
                value.extractTexts().map {
                    it.copy(pointer = it.pointer.markMap(key))
                }
            } // wrap inner pointer
    }

    is NbtString -> {
        sequenceOf(PointerWithExtension(DataPointer.Terminator, value, false))
    }

    else -> emptySequence()
}

