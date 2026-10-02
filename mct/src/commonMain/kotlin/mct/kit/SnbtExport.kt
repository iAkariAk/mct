package mct.kit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import mct.MCTWorkspace
import mct.util.IO
import mct.util.toSnbt
import okio.Path

suspend fun MCTWorkspace.exportRegionSnbt(outputDir: Path) = coroutineScope {
    dimensions.forEach { (_, dimension) ->
        listOfNotNull(dimension.regionRawMgr, dimension.poiRawMgr, dimension.entitiesRawMgr).forEach { mgr ->
            launch(Dispatchers.IO) {
                val relative = mgr.path.relativeTo(rootDir)
                val dir = outputDir / relative
                fs.createDirectories(dir)
                mgr.regions().forEach { region ->
                    val target = dir / ("${region.inferFilename()}.txt")
                    fs.write(target) {
                        region.chunks.forEachIndexed { index, chunk ->
                            writeUtf8("Index $index: \n")
                            val data = chunk?.data?.fold(
                                ifLeft = { e ->
                                    e.stackTraceToString()
                                },
                                ifRight = { data ->
                                    data.toSnbt(true)
                                }
                            ) ?: "<empty_chunk>"
                            writeUtf8(data)
                            writeUtf8("\n\n")
                        }
                    }
                }
            }
        }
    }
}
