package mct.gui.services

import io.github.yuroyami.kiteimage.KiteBitmap
import io.github.yuroyami.kiteimage.KiteImage
import kotlinx.coroutines.withContext
import mct.Env
import mct.gui.model.MapImageFormat
import mct.gui.platform.ioDispatcher
import mct.gui.util.writeAtomically
import mct.map.MapColors
import mct.map.MapFile
import okio.Path.Companion.toPath

/**
 * The `mct kit map` feature on the CLI's own model.
 *
 * The GUI reads and writes [MapFile] directly (the CLI's `MapCommands` does the same) and renders
 * the map with KiteImage, which is what a desktop preview needs and a console command cannot
 * provide. Nothing about the map format is re-implemented: decoding, the color table and the
 * color quantization all come from `mct.map`.
 */

/** Decode a Minecraft map file (`data/map_<n>.dat`, NBT gzip). */
context(env: Env)
suspend fun readMapFile(path: String): MapFile = withContext(ioDispatcher) {
    with(env) { MapFile.decodeFromFile(path.toPath()) }
}

/**
 * Encode [mapFile] back to [path], replacing it through a sibling temp file first.
 *
 * A map file is world data the user can lose, so it goes through the same temp-then-move every
 * other file this app replaces uses.
 */
context(env: Env)
suspend fun writeMapFile(path: String, mapFile: MapFile) = withContext(ioDispatcher) {
    writeAtomically(env.fs, path.toPath()) { temp ->
        with(env) { mapFile.encodeToFile(temp) }
    }
}

/** Write an exported preview to [path], likewise through a temp file. */
context(env: Env)
suspend fun writeMapImage(path: String, bytes: ByteArray) = withContext(ioDispatcher) {
    writeAtomically(env.fs, path.toPath()) { temp ->
        env.fs.write(temp) { write(bytes) }
    }
}

/**
 * The image of [mapFile]'s colors, at the map canvas size.
 *
 * Every preview in the GUI is built from this, never from an imported file: what is drawn is
 * exactly what the map stores, so the quantization an edit goes through is visible in it.
 */
fun mapBitmap(mapFile: MapFile): KiteBitmap = KiteBitmap(
    MapFile.MAP_SIZE,
    MapFile.MAP_SIZE,
    mapFile.data.colors.toRGBArray(0xFF.toByte()),
)

/**
 * [bitmap] as map colors, quantizing each pixel to Minecraft's 61 base colors × 4 shades.
 *
 * Rejects any size other than the 128×128 map canvas, which is the one thing the CLI's
 * `map edit` checks before it overwrites a map.
 */
fun mapColors(bitmap: KiteBitmap): MapColors {
    require(bitmap.width == MapFile.MAP_SIZE && bitmap.height == MapFile.MAP_SIZE) {
        "图片尺寸必须是 ${MapFile.MAP_SIZE}×${MapFile.MAP_SIZE}，当前为 ${bitmap.width}×${bitmap.height}"
    }
    return MapColors.fromRGBArray(bitmap.argb)
}

/** Encode [bitmap] as [format], through the same codecs `mct kit map view` writes. */
fun encodeMapImage(bitmap: KiteBitmap, format: MapImageFormat): ByteArray = when (format) {
    MapImageFormat.Png -> KiteImage.encodePng(bitmap)
    MapImageFormat.Bmp -> KiteImage.encodeBmp(bitmap)
    MapImageFormat.Jpeg -> KiteImage.encodeJpeg(bitmap)
    MapImageFormat.Gif -> KiteImage.encodeGif(bitmap)
}

/**
 * Decode an image to be written into a map.
 *
 * EXIF orientation is applied, so a photo that arrived rotated is not placed sideways; any format
 * KiteImage reads is accepted, exactly as `mct kit map edit` accepts one.
 */
fun decodeMapImage(bytes: ByteArray): KiteBitmap = KiteImage.decode(bytes, applyOrientation = true)

/** One line describing [mapFile], so the dialog shows which file the preview belongs to. */
fun mapSummary(mapFile: MapFile): String = buildString {
    append("DataVersion ${mapFile.dataVersion}")
    append(" · 缩放 ${mapFile.data.scale}")
    append(" · ${mapFile.data.dimension}")
    append(" · 中心 (${mapFile.data.xCenter}, ${mapFile.data.zCenter})")
    append(if (mapFile.data.locked) " · 已锁定" else " · 未锁定")
    if (mapFile.data.banners.isNotEmpty()) append(" · 旗帜 ${mapFile.data.banners.size}")
    if (mapFile.data.frames.isNotEmpty()) append(" · 展示框 ${mapFile.data.frames.size}")
}
