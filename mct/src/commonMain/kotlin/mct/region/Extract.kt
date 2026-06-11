package mct.region

import arrow.core.raise.Raise
import arrow.core.raise.context.either
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.*
import mct.FormatKind
import mct.MCTWorkspace
import mct.RegionExtraction
import mct.RegionExtractionGroup
import mct.dp.mcfunction.*
import mct.pointer.*
import mct.region.anvil.Coord
import mct.region.anvil.model.ChunkDataKind
import mct.text.isTextCompound
import mct.text.isTextCompoundShorthanded
import mct.util.StringIndices
import mct.util.snbt.SnbtCompound
import mct.util.snbt.SnbtList
import mct.util.snbt.SnbtString
import mct.util.snbt.SnbtTag
import mct.util.toSnbt
import net.benwoodworth.knbt.NbtCompound
import net.benwoodworth.knbt.NbtList
import net.benwoodworth.knbt.NbtString
import net.benwoodworth.knbt.NbtTag
import kotlin.jvm.JvmName


context(_: Raise<ExtractError>)
fun MCTWorkspace.extractFromRegion(
    regionPatterns: List<DataPointerPattern>? = BuiltinRegionPatterns,
    mcfPatterns: ExtractPatternSet = BuiltinMCFPatterns,
    mcfDataPatterns: List<DataPointerPattern>? = BuiltinMCFunctionDataPatterns
): Flow<RegionExtractionGroup> {
    if (regionPatterns == null) logger.warning { "The filter was disabled, which causes export all string from the region" }
    logger.info { "Extracting from ${dimensions.size} dimensions" }

    return dimensions.values.asFlow().flatMapMerge { dimension ->
        flowOf(
            dimension.regionRawMgr to ChunkDataKind.Terrain,
            dimension.poiRawMgr to ChunkDataKind.Poi,
            dimension.entitiesRawMgr to ChunkDataKind.Entities
        )
            .filter { (manager, _) -> manager != null }
            .flatMapMerge(3) { (manager, kind) ->
                manager!!.regions().asFlow().flatMapMerge(8) { region ->
                    val extractions = region.chunks.asSequence()
                        .filterNotNull()
                        .flatMap { chunk ->
                            chunk.data.extractTexts()
                                .mapNotNull { (pointer, content, kind, type) ->
                                    when (type) {
                                        PointerWithExtension.Type.Command -> either {
                                            val cmds = context(logger) {
                                                parseMCFunction(content)
                                            }
                                            RegionExtraction.Command(
                                                index = chunk.index,
                                                pointer = pointer,
                                                raw = content,
                                                locations = cmds.flatMap {
                                                    extractTextFromCommand(
                                                        it,
                                                        mcfPatterns,
                                                        mcfDataPatterns
                                                    )
                                                }.takeIf { it.isNotEmpty() }
                                                    ?.map {
                                                        RegionExtraction.Command.Location(it.indices, it.content)
                                                    } ?: return@mapNotNull null
                                            )
                                        }.getOrNull()

                                        PointerWithExtension.Type.Text if pointer.matches(regionPatterns) -> RegionExtraction.Text(
                                            index = chunk.index,
                                            pointer = pointer,
                                            kind = kind,
                                            content = content
                                        )

                                        else -> null
                                    }

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
    val kind: FormatKind = FormatKind.Str,
    val type: Type = Type.Text,
) {
    enum class Type {
        Command, Text
    }
}

internal inline fun Sequence<PointerWithExtension>.filterPointer(patterns: Iterable<DataPointerPattern>?) =
    filter { (ptr, _, _) -> ptr.matches(patterns) }

internal fun NbtTag.extractTexts(): Sequence<PointerWithExtension> = when (this) {
    is NbtList<*> -> asSequence().withIndex().flatMap { (index, tag) ->
        tag.extractTexts().map {
            it.copy(pointer = it.pointer.markArray(index))
        }
    } // wrap inner pointer

    is NbtCompound -> {
        if (isTextCompound()) {
            sequenceOf(PointerWithExtension(DataPointer.Terminator, toSnbt(), FormatKind.Nbt))
        } else if (isTextCompoundShorthanded()) {
            val map = toMutableMap()
            val text = map.remove("")
            map["text"] = text!!
            val expanded = NbtCompound(map)

            sequenceOf(PointerWithExtension(DataPointer.Terminator, expanded.toSnbt(), FormatKind.Nbt))
        } else {
            asSequence().flatMap { (key, value) ->
                if (key == "Command" && value is NbtString) {
                    val pwe = PointerWithExtension(
                        DataPointer.Terminator,
                        value.value,
                        FormatKind.Str,
                        PointerWithExtension.Type.Command
                    )
                    return@flatMap sequenceOf(pwe)
                }
                value.extractTexts().map {
                    it.copy(pointer = it.pointer.markMap(key))
                }
            } // wrap inner pointer
        }
    }

    is NbtString -> {
        sequenceOf(PointerWithExtension(DataPointer.Terminator, value))
    }

    else -> emptySequence()
}

// Cope from the above
// due to using IR dragging slow performance

internal data class PointerWithExtensionForSnbt(
    val pointer: DataPointer,
    override val indices: IntRange,
    override val content: String,
    val kind: FormatKind = FormatKind.Str,
) : StringIndices

@JvmName($$"filterPointer$snbt")
internal inline fun Sequence<PointerWithExtensionForSnbt>.filterPointer(patterns: Iterable<DataPointerPattern>?) =
    filter { (ptr, _, _) -> ptr.matches(patterns) }

internal fun SnbtTag.extractTexts(snbt: String): Sequence<PointerWithExtensionForSnbt> = when (this) {
    is SnbtList -> asSequence().withIndex().flatMap { (index, tag) ->
        tag.extractTexts(snbt).map {
            it.copy(pointer = it.pointer.markArray(index))
        }
    } // wrap inner pointer

    is SnbtCompound -> {
        if (isTextCompound()) {
            sequenceOf(
                PointerWithExtensionForSnbt(
                    DataPointer.Terminator,
                    indices,
                    snbt.substring(indices),
                    FormatKind.Nbt
                )
            )
        } else if (isTextCompoundShorthanded()) {
            val map = toMutableMap()
            val text = map.remove("")
            map["text"] = text!!
            val expanded = SnbtCompound(indices, map)

            sequenceOf(
                PointerWithExtensionForSnbt(
                    DataPointer.Terminator,
                    indices,
                    snbt.substring(indices),
                    FormatKind.Nbt
                )
            )
        } else {
            asSequence().flatMap { (key, value) ->
                value.extractTexts(snbt).map {
                    it.copy(pointer = it.pointer.markMap(key))
                }
            } // wrap inner pointer
        }
    }

    is SnbtString -> {
        sequenceOf(PointerWithExtensionForSnbt(DataPointer.Terminator, indices, rawContent))
    }

    else -> emptySequence()
}

