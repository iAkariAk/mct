package mct.kit

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import mct.MCTWorkspace
import mct.util.toSnbt
import okio.Path

suspend fun MCTWorkspace.exportRegionSnbt(outputDir: Path) = coroutineScope {
    dimensions.forEach { (_, dimension) ->
        listOfNotNull(dimension.regionRawMgr, dimension.poiRawMgr, dimension.entitiesRawMgr).forEach { mgr ->
            launch {
                val relative = mgr.path.relativeTo(rootDir)
                val dir = outputDir / relative
                fs.createDirectories(dir)
                mgr.regions().forEach { region ->
                    val target = dir / region.inferFilename()
                    fs.write(target) {
                        val output = region.chunks.withIndex().joinToString("\n\n") { (index, chunk) ->
                            val data = chunk?.data?.toSnbt(true) ?: "<empty_chunk>"
                            "Index $index:\n$data"
                        }
                        writeUtf8(output)
                    }
                }
            }
        }
    }
}
