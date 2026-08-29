@file:OptIn(FlowPreview::class)

package mct.cext

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import mct.MCTWorkspace
import mct.dp.mcfunction.backfillMCFunction
import mct.dp.mcjson.backfillMCJson
import mct.model.patch.CextReplacementGroup
import mct.model.patch.DatapackReplacement
import mct.model.patch.NbtReplacement
import mct.model.patch.SnbtReplacement
import mct.nbt.backfill
import mct.nbt.backfillSnbt
import mct.util.IO
import net.benwoodworth.knbt.NbtTag
import net.benwoodworth.knbt.decodeFromSource
import net.benwoodworth.knbt.encodeToSink
import okio.BufferedSource

@Suppress("UNCHECKED_CAST")
suspend fun MCTWorkspace.backfillCext(
    replacementGroups: Iterable<CextReplacementGroup>
) = coroutineScope {
    replacementGroups.forEach { (pathStr, kind, replacements) ->
        launch(Dispatchers.IO) {
            val path = rootDir / pathStr

            when (kind) {
                is CextFormatKind.MCFunction -> {
                    val raw = fs.read(path, BufferedSource::readUtf8)
                    val backfilled = raw.backfillMCFunction(replacements as List<DatapackReplacement.MCFunction>)
                    fs.write(path) {
                        writeUtf8(backfilled)
                    }
                }

                is CextFormatKind.MCJson -> {
                    val raw = fs.read(path, BufferedSource::readUtf8)
                    val backfilled = raw.backfillMCJson(replacements as List<DatapackReplacement.MCJson>)
                    fs.write(path) {
                        writeUtf8(backfilled)
                    }
                }

                is CextFormatKind.Snbt -> {
                    val raw = fs.read(path, BufferedSource::readUtf8)
                    val backfilled = raw.backfillSnbt(replacements as List<SnbtReplacement>)
                    fs.write(path) {
                        writeUtf8(backfilled)
                    }
                }

                is CextFormatKind.Nbt -> {
                    val nbtSerializer = kind.compression.nbtSerializer
                    val raw = fs.read<NbtTag>(path, nbtSerializer::decodeFromSource)
                    val backfilled = raw.backfill(replacements as List<NbtReplacement>)
                    fs.write(path) {
                        nbtSerializer.encodeToSink(backfilled, this)
                    }
                }
            }
        }
    }
}