package mct.gui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import mct.Env
import mct.LoggerLevel
import mct.gui.model.MapImageFormat
import mct.gui.model.MapToolStatus
import mct.gui.platform.ioDispatcher
import mct.gui.services.*
import mct.map.MapFile
import okio.Path.Companion.toPath

/**
 * The map tool as a state holder: the decoded [MapFile] its preview is drawn from, the path it was
 * loaded from, the last action's outcome, and the three actions the page raises.
 *
 * The decoded map is what is held, rather than the file an edit was imported from, because the
 * preview is rendered from the map's own colors: an imported image is quantized into `MapColors`
 * first and the preview then shows those colors, so a color the map cannot store is visible before
 * anything is written.
 *
 * Every action runs on [OperationRunner] like the rest of the toolbox, so the console records it and
 * the page's buttons disable while it lasts. Its outcome is reported through [status] rather than a
 * snackbar: the page is a function area of its own, so a message raised elsewhere would be easy to
 * miss.
 */
class MapToolController(
    private val env: Env,
    private val operations: OperationRunner,
) {
    /** The decoded map; `null` until a load succeeds. */
    var mapFile by mutableStateOf<MapFile?>(null)
        private set

    /**
     * The file [mapFile] came from. An overwrite is written back to exactly this path, not to
     * whatever the page's path field currently holds: the field can be edited to point elsewhere
     * while the preview still shows this map.
     */
    var loadedPath by mutableStateOf<String?>(null)
        private set

    /** Outcome of the last action, shown inside the map tool's page. */
    var status by mutableStateOf<MapToolStatus?>(null)
        private set

    /** Decode [path], replacing whatever was loaded before. */
    fun load(path: String) = action {
        val decoded = with(env) { readMapFile(path) }
        mapFile = decoded
        loadedPath = path
        env.logger.info { "已加载地图文件: $path" }
        MapToolStatus("已加载地图文件: $path")
    }

    /** Write the held map's colors to [output] as an image in [format]. */
    fun saveImage(output: String, format: MapImageFormat) = action {
        val bytes = encodeMapImage(mapBitmap(requireMap()), format)
        with(env) { writeMapImage(output, bytes) }
        env.logger.info { "已导出地图图像（${format.label}）: $output" }
        MapToolStatus("已导出地图图像（${format.label}）: $output")
    }

    /**
     * Quantize [imagePath] into the held map's colors and write the map back to [loadedPath].
     *
     * This is the CLI's `map edit`: the image's pixels become the map's colors, shrunk to what
     * Minecraft can store. The held map is replaced by the result, so the preview shows the
     * quantized colors rather than the image that was picked.
     */
    fun overwriteImage(imagePath: String) = action {
        val map = requireMap()
        val path = loadedPath ?: error("请先加载地图文件")
        // Reading, decoding and quantizing a photo is heavy and must not run on the UI dispatcher:
        // the size check inside `mapColors` only fires after the whole image has been decoded, so a
        // wrongly sized pick would otherwise freeze the window for the length of the decode.
        val updated = withContext(ioDispatcher) {
            val bytes = env.fs.read(imagePath.toPath()) { readByteArray() }
            map.copy(data = map.data.copy(colors = mapColors(decodeMapImage(bytes))))
        }
        with(env) { writeMapFile(path, updated) }
        mapFile = updated
        env.logger.info { "已复写地图图像: $imagePath -> $path" }
        MapToolStatus("已复写地图图像: $path")
    }

    /**
     * Run one action, reporting its outcome through [status].
     *
     * The failure is handled here rather than left to [OperationRunner]: the runner reports failures
     * with a snackbar, which the tool's page would hide behind its own scroll. It is still logged
     * with its trace, so nothing is lost for diagnosis, and the message the user needs (a wrong
     * image size, an undecodable map) lands in the page they are looking at.
     */
    private fun action(block: suspend () -> MapToolStatus) = operations.launch {
        try {
            status = block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            env.logger.log(LoggerLevel.Error, e.stackTraceToString())
            status = MapToolStatus(e.message ?: "地图操作失败", error = true)
        }
    }

    private fun requireMap(): MapFile = mapFile ?: error("请先加载地图文件")
}
