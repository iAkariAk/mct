package mct.region

import arrow.core.raise.Raise
import arrow.core.raise.context.either
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import mct.*
import mct.command.extractTextFromCommand
import mct.command.parseMCFunction
import mct.nbt.PointerWithExtension
import mct.nbt.extractTexts
import mct.pointer.matches
import mct.region.anvil.Coord
import mct.region.anvil.model.ChunkDataKind
import mct.util.IO


context(_: Raise<ExtractError>)
fun MCTWorkspace.extractFromRegion(
    pattern: MCTPattern = MCTPattern.Default,
): Flow<RegionExtractionGroup> {
    if (pattern.region == null) logger.warning { "The filter was disabled, which causes export all string from the region" }
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
                                            NbtExtraction.Command(
                                                pointer = pointer,
                                                raw = content,
                                                locations = cmds.flatMap {
                                                    extractTextFromCommand(
                                                        it,
                                                        pattern.mcfunction,
                                                        pattern.mcfunctionData
                                                    )
                                                }.takeIf { it.isNotEmpty() }
                                                    ?.map {
                                                        NbtExtraction.Command.Location(
                                                            it.indices,
                                                            it.content,
                                                            it.syntax
                                                        )
                                                    } ?: return@mapNotNull null
                                            )
                                        }.getOrNull()

                                        PointerWithExtension.Type.Text if pointer.matches(pattern.region) -> NbtExtraction.Text(
                                            pointer = pointer,
                                            kind = kind,
                                            content = content
                                        )

                                        else -> null
                                    }?.let {
                                        RegionExtraction(index = chunk.index, nbt = it)
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
                }
            }
    }.flowOn(Dispatchers.IO)
}
