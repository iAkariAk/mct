package mct.gui.util

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.kmpalette.palette.graphics.Palette
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mct.gui.model.GuiSettings
import mct.gui.services.ThemeSettings
import mct.gui.services.themeSetting
import okio.FileSystem
import okio.Path.Companion.toPath
import org.jetbrains.skia.*

// ── Reactive theme state ──────────────────────────────────────

/**
 * Reactive theme state — holds the currently active [ColorScheme]
 * so that [Main.kt] can pick it up reactively.
 */
object ThemeState {
    var colorScheme: ColorScheme? by mutableStateOf(null)

    /** Build a scheme from [argb] and apply it. */
    private fun applyArgb(argb: Int, isDark: Boolean) {
        colorScheme = dynamicColorScheme(
            seedColor = Color(argb),
            isDark = isDark,
            style = PaletteStyle.Vibrant,
        )
    }

    /** Called when the user picks an image — writes [argb] into settings + scheme. */
    fun applySeedArgb(argb: Int, isDark: Boolean = true) {
        GuiSettings.seedColorArgb = argb
        GuiSettings.isDynamicThemeEnabled = true
        applyArgb(argb, isDark)
    }

    /** Called on startup — rebuilds the scheme from the persisted seed (if any). */
    fun restoreFromSettings(isDark: Boolean = true) {
        val argb = GuiSettings.seedColorArgb
        if (argb != 0) applyArgb(argb, isDark)
    }

    fun reset() {
        GuiSettings.seedColorArgb = 0
        GuiSettings.isDynamicThemeEnabled = false
        colorScheme = null
    }
}

// ── Image → seed colour ───────────────────────────────────────

/**
 * Target pixel count for colour extraction — 128×128 is more than
 * enough for KMPalette to find vibrant / muted swatches.
 */
private const val SAMPLE_SIZE = 128

/**
 * Decode an image and scale it down to at most [SAMPLE_SIZE]² pixels
 * (via Skia's native scaler), run [Palette] (KMPalette) over the
 * samples, and return the most distinctive colour as a [Color].
 *
 * The whole function runs on [Dispatchers.IO] — it never blocks the
 * UI thread.  Native scaling means we NEVER allocate the full-size
 * pixel buffer, making this safe for huge 4K / 8K wallpapers.
 */
suspend fun extractSeedColorFromFile(path: String): Color? = withContext(Dispatchers.IO) {
    try {
        val bytes = FileSystem.SYSTEM.read(path.toPath()) { readByteArray() }
        val src = Image.makeFromEncoded(bytes)

        // Target sample dimensions
        val scale = minOf(1f, SAMPLE_SIZE.toFloat() / maxOf(src.width, src.height))
        val sw = (src.width * scale).toInt().coerceAtLeast(1)
        val sh = (src.height * scale).toInt().coerceAtLeast(1)

        // Allocate a SMALL bitmap and scale the image into it directly
        val dstInfo = ImageInfo(sw, sh, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
        val dstBitmap = Bitmap().apply { allocPixels(dstInfo) }
        val pixmap = dstBitmap.peekPixels() ?: return@withContext null
        src.scalePixels(pixmap, SamplingMode.DEFAULT, false)

        // Read the small pixel buffer (≤ SAMPLE_SIZE² × 4 bytes ≈ 64 KB)
        val pixelBytes = dstBitmap.readPixels() ?: return@withContext null
        val intPixels = IntArray(sw * sh) { rgbaOffset ->
            val off = rgbaOffset * 4
            val r = pixelBytes[off].toInt() and 0xFF
            val g = pixelBytes[off + 1].toInt() and 0xFF
            val b = pixelBytes[off + 2].toInt() and 0xFF
            val a = pixelBytes[off + 3].toInt() and 0xFF
            (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        val palette = Palette.from(intPixels, sw, sh)
            .maximumColorCount(16)
            .generate()

        val rgb = palette.vibrantSwatch?.rgb
            ?: palette.mutedSwatch?.rgb
            ?: palette.dominantSwatch?.rgb
            ?: palette.darkVibrantSwatch?.rgb
            ?: palette.lightMutedSwatch?.rgb

        if (rgb != null) Color(rgb) else null
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

// ── Image theme state (UI-bound lifecycle) ────────────────────

/**
 * Manages the loading + extraction lifecycle exposed to the settings sheet UI.
 * Keeps processing / error state but does NOT duplicate seed colour – it lives in [ThemeState].
 */
class ImageThemeState {
    var isProcessing by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    suspend fun loadFromPath(path: String) {
        isProcessing = true
        errorMessage = null
        try {
            val color = extractSeedColorFromFile(path)
            if (color != null) {
                val argb = color.toArgb()
                ThemeState.applySeedArgb(argb)
                withContext(Dispatchers.IO) { themeSetting.save(ThemeSettings(seedColorArgb = argb)) }
            } else {
                errorMessage = "无法从图片中提取主题色"
            }
        } catch (e: Exception) {
            errorMessage = "处理失败: ${e.message}"
        } finally {
            isProcessing = false
        }
    }

    /** Reset theme and persist the cleared state. */
    fun reset() {
        isProcessing = false
        errorMessage = null
        ThemeState.reset()
        runCatching { themeSetting.save(ThemeSettings(seedColorArgb = 0)) }
    }
}

@Composable
fun rememberImageThemeState(): ImageThemeState = remember { ImageThemeState() }

// ── Wallpaper detection ───────────────────────────────────────

fun getWallpaperPath(): String? {
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
