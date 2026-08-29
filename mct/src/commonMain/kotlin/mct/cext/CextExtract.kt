@file:OptIn(FlowPreview::class)

package mct.cext

import arrow.fx.coroutines.parMapNotNullUnordered
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.serialization.decodeFromString
import mct.MCTPattern
import mct.MCTWorkspace
import mct.command.extractTextFromCommands
import mct.dp.mcjson.extractTextFromMCJson
import mct.model.patch.CextExtractionGroup
import mct.model.patch.DatapackExtraction
import mct.model.patch.NbtExtraction
import mct.model.patch.SnbtExtraction
import mct.nbt.extractText
import mct.serializer.Snbt
import net.benwoodworth.knbt.NbtTag
import net.benwoodworth.knbt.decodeFromSource
import okio.BufferedSource

fun MCTWorkspace.extractByCext(pattern: MCTPattern): Flow<CextExtractionGroup> {
    val cextPatterns = pattern.cext?.flatten()
    if (cextPatterns.isNullOrEmpty()) return emptyFlow()
    return fs.listRecursively(rootDir).asFlow()
        .filter { !fs.metadata(it).isDirectory }
        .parMapNotNullUnordered { path ->
            val pathStr = path.relativeTo(rootDir).toString()
            val cextPattern =
                cextPatterns.find { it.patternRegex.matches(pathStr) } ?: return@parMapNotNullUnordered null
            val cextKind = cextPattern.kind
            val extractions = when (cextKind) {
                is CextFormatKind.MCFunction -> {
                    val mcf = fs.read(path, BufferedSource::readUtf8)
                    extractTextFromCommands(mcf, cextKind.attachTo(pattern)).map { extracted ->
                        DatapackExtraction.MCFunction(
                            extracted.indices,
                            extracted.content,
                            extracted.syntax,
                            extracted.format
                        )
                    }
                }

                is CextFormatKind.MCJson -> {
                    val json = fs.read(path, BufferedSource::readUtf8)
                    extractTextFromMCJson(json, cextKind.attachTo(pattern)).map(DatapackExtraction::MCJson).toList()
                }

                is CextFormatKind.Nbt -> {
                    val nbtSerializer = cextKind.compression.nbtSerializer
                    val tag = fs.read<NbtTag>(path, nbtSerializer::decodeFromSource)
                    tag.extractText(cextKind.attachTo(pattern)).map {
                        NbtExtraction(cextKind.compression, it)
                    }.toList()
                }

                is CextFormatKind.Snbt -> {
                    val snbt = fs.read(path, BufferedSource::readUtf8)
                    val tag = Snbt.decodeFromString<NbtTag>(snbt)
                    tag.extractText(cextKind.attachTo(pattern)).map(::SnbtExtraction).toList()
                }
            }
            CextExtractionGroup(pathStr, cextKind, extractions)
        }
}