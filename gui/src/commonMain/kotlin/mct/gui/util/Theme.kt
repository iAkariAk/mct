package mct.gui.util

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import com.kmpalette.palette.graphics.Palette
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import mct.gui.model.GuiSettings
import mct.gui.platform.ImageSamples
import mct.gui.platform.decodeImagePixels
import mct.gui.platform.ioDispatcher
import mct.gui.platform.loadWallpaperPixels
import mct.gui.services.ThemeSettings
import mct.gui.services.themeSetting

/**
 * Reactive theme state — holds the currently active [ColorScheme]
 * so that the application shell can pick it up reactively.
 */
object ThemeState {
    var colorScheme: ColorScheme? by mutableStateOf(null)

    /** Latest system light/dark state, so runtime picks build a scheme matching it. */
    var isDark by mutableStateOf(true)
        private set

    /** Build a scheme from [argb] and apply it. */
    private fun applyArgb(argb: Int, isDark: Boolean) {
        colorScheme = dynamicColorScheme(
            seedColor = Color(argb),
            isDark = isDark,
            style = PaletteStyle.Vibrant,
        )
    }

    /** Called when the user picks an image — writes [argb] into settings + scheme. */
    fun applySeedArgb(argb: Int) {
        GuiSettings.seedColorArgb = argb
        GuiSettings.isDynamicThemeEnabled = true
        applyArgb(argb, isDark)
    }

    /** Called on startup and whenever the system theme or the seed changes. */
    fun restoreFromSettings(isDark: Boolean) {
        this.isDark = isDark
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
 * Manages the loading + extraction lifecycle exposed to the settings sheet UI.
 * Keeps processing / error state but does NOT duplicate seed colour – it lives in [ThemeState].
 *
 * Decoding is a platform seam: desktop scales through Skia, Android through `BitmapFactory`. Both
 * hand back the same swatch input, so the colour choice below is one implementation.
 */
class ImageThemeState {
    var isProcessing by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    suspend fun loadFromPath(path: String) =
        load(failure = "无法从图片中提取主题色") { decodeImagePixels(path, SAMPLE_SIZE) }

    /** Desktop-only in practice: [mct.gui.platform.supportsWallpaperTheme] hides the button elsewhere. */
    suspend fun loadFromWallpaper() =
        load(failure = "无法读取壁纸") { loadWallpaperPixels(SAMPLE_SIZE) }

    private suspend inline fun load(failure: String, decode: () -> ImageSamples?) {
        isProcessing = true
        errorMessage = null
        try {
            val samples = decode()
            val rgb = samples?.let {
                val palette = Palette.from(it.pixels, it.width, it.height)
                    .maximumColorCount(16)
                    .generate()
                palette.vibrantSwatch?.rgb
                    ?: palette.mutedSwatch?.rgb
                    ?: palette.dominantSwatch?.rgb
                    ?: palette.darkVibrantSwatch?.rgb
                    ?: palette.lightMutedSwatch?.rgb
            }
            if (rgb != null) {
                ThemeState.applySeedArgb(rgb)
                persistTheme()
            } else {
                errorMessage = failure
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorMessage = "处理失败: ${e.message}"
        } finally {
            isProcessing = false
        }
    }

    /** Reset theme and persist the cleared state. */
    suspend fun reset() {
        isProcessing = false
        errorMessage = null
        ThemeState.reset()
        persistTheme()
    }

    /**
     * Write the theme settings right away; picking a colour is a deliberate action, so it
     * should not wait for the auto-save debounce.
     */
    private suspend fun persistTheme() = withContext(ioDispatcher) {
        themeSetting.save(
            ThemeSettings(
                seedColorArgb = GuiSettings.seedColorArgb,
                isDynamicThemeEnabled = GuiSettings.isDynamicThemeEnabled,
                isRainbowTheme = GuiSettings.isRainbowTheme,
            )
        )
    }
}

@Composable
fun rememberImageThemeState(): ImageThemeState = remember { ImageThemeState() }
