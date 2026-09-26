package mct.gui.platform

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import okio.Path
import okio.Path.Companion.toPath
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.SamplingMode

/** Desktop's settings live beside the user's home, as the CLI docs describe. */
actual val settingsDirectory: Path get() = "${System.getProperty("user.home")}/.mct/".toPath()

/** Relative paths the user types resolve against the process working directory. */
actual val appWorkingDirectory: String get() = System.getProperty("user.dir")

actual val supportsWallpaperTheme: Boolean get() = true

actual fun formatEpochDate(epochMillis: Long): String =
    java.time.Instant.ofEpochMilli(epochMillis)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate()
        .toString()

private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

actual fun revealInFileExplorer(path: String): Boolean = runCatching {
    val file = java.io.File(path)
    if (isWindows) {
        // Explorer selects the file when it is given `/select,<path>`; handing it the file itself
        // would open the file instead of showing where it is.
        val argument = if (file.isDirectory) file.path else "/select,${file.path}"
        ProcessBuilder("explorer.exe", argument)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
    } else {
        java.awt.Desktop.getDesktop().open(if (file.isDirectory) file else file.parentFile ?: file)
    }
}.isSuccess

/**
 * Decode the image and reduce it to at most [maxSize] on its longest side.
 *
 * Skia scales while decoding into the small destination bitmap, so a 4K wallpaper never allocates
 * its full-resolution pixel buffer. KMPalette then picks the swatch the way the theme code expects.
 */
actual suspend fun decodeImagePixels(path: String, maxSize: Int): ImageSamples? =
    withContext(ioDispatcher) {
        try {
            val bytes = platformFileSystem.read(path.toPath()) { readByteArray() }
            // Skiko holds native memory (a full-size decode is tens of megabytes each), so the
            // images are closed here instead of waiting for the GC to run their cleaners.
            Image.makeFromEncoded(bytes).use { src ->
                val scale = minOf(1f, maxSize.toFloat() / maxOf(src.width, src.height))
                val sw = (src.width * scale).toInt().coerceAtLeast(1)
                val sh = (src.height * scale).toInt().coerceAtLeast(1)

                val dstInfo = ImageInfo(sw, sh, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
                Bitmap().use { dstBitmap ->
                    dstBitmap.allocPixels(dstInfo)
                    val pixmap = dstBitmap.peekPixels() ?: return@withContext null
                    src.scalePixels(pixmap, SamplingMode.DEFAULT, false)

                    val pixelBytes = dstBitmap.readPixels() ?: return@withContext null
                    val intPixels = IntArray(sw * sh) { rgbaOffset ->
                        val off = rgbaOffset * 4
                        val r = pixelBytes[off].toInt() and 0xFF
                        val g = pixelBytes[off + 1].toInt() and 0xFF
                        val b = pixelBytes[off + 2].toInt() and 0xFF
                        val a = pixelBytes[off + 3].toInt() and 0xFF
                        (a shl 24) or (r shl 16) or (g shl 8) or b
                    }
                    ImageSamples(intPixels, sw, sh)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

actual suspend fun loadWallpaperPixels(maxSize: Int): ImageSamples? =
    wallpaperPathOrNull()?.let { decodeImagePixels(it, maxSize) }

private fun wallpaperPathOrNull(): String? {
    val userHome = System.getProperty("user.home")

    // Windows
    val appData = System.getenv("APPDATA")
    for (base in listOfNotNull(appData, "$userHome\\AppData\\Roaming")) {
        val candidate = "$base\\Microsoft\\Windows\\Themes\\TranscodedWallpaper"
        if (java.io.File(candidate).exists()) return candidate
    }

    // Linux
    try {
        val proc = Runtime.getRuntime().exec(
            arrayOf("gsettings", "get", "org.gnome.desktop.background", "picture-uri")
        )
        val uri = proc.inputStream.bufferedReader().readText().trim()
            .removeSurrounding("'").removePrefix("file://")
        if (uri.isNotBlank() && java.io.File(uri).exists()) return uri
    } catch (_: Exception) {
    }

    // macOS
    for (name in listOf("desktop.jpg", "Background.png", "Wallpaper.jpg")) {
        val path = "$userHome/Library/Application Support/Dock/$name"
        if (java.io.File(path).exists()) return path
    }

    return null
}

actual fun platformPathOf(file: PlatformFile): String = file.absolutePath()
