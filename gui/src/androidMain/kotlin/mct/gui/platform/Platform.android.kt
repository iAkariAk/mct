package mct.gui.platform

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import kotlinx.coroutines.withContext
import okio.Path
import okio.Path.Companion.toPath
import mct.gui.services.apiModule
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import mct.gui.components.AppTitleBar
import mct.gui.components.AppTitleBarAccent

/**
 * The application context, set once by the Android application before any GUI code runs.
 *
 * The platform seams below need it for `filesDir` and for resolving picked documents, and neither
 * has a `Context` parameter to take it from.
 */
internal lateinit var appContext: Context
    private set

/**
 * Called from the Android application's `onCreate`, before `setContent`.
 *
 * Starts Koin as well: the view model resolves its client manager from it, so the graph has to exist
 * before the first composition. It lives here rather than in the app module because Koin is a GUI
 * dependency the app module does not see.
 */
fun initAndroidPlatform(context: Context) {
    appContext = context.applicationContext
    if (GlobalContext.getOrNull() == null) {
        startKoin { modules(apiModule) }
    }
}

/** Settings live in the app's private storage; `/` is neither writable nor useful on Android. */
actual val settingsDirectory: Path get() = (appContext.filesDir.absolutePath + "/mct/").toPath()

/**
 * Relative paths the user types resolve under the app's private files directory.
 *
 * Android's process working directory is `/`, which nothing can write to, so a relative path the
 * user typed would fail for a reason that is not visible in the UI.
 */
actual val appWorkingDirectory: String get() = appContext.filesDir.absolutePath

/**
 * Android's wallpaper is not offered: `WallpaperManager` needs `READ_EXTERNAL_STORAGE` on API
 * 24-29 and `MANAGE_EXTERNAL_STORAGE` from 30, and several ROMs hand back a re-compressed
 * approximation. Reporting that up front keeps the button from failing silently.
 */
actual val supportsWallpaperTheme: Boolean get() = false

actual fun formatEpochDate(epochMillis: Long): String =
    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(java.util.Date(epochMillis))

/**
 * Android has no public "reveal in the file manager" intent — the closest is opening the file with
 * a `content://` URI from a `FileProvider`, which shows the file rather than where it lives. Saying
 * so lets the caller fall back to its own message instead of the platform doing nothing.
 */
actual fun revealInFileExplorer(path: String): Boolean = false

/**
 * Decode the image at [path] into ARGB pixels scaled to at most [maxSize] on its longest side.
 *
 * `BitmapFactory` reads the header first so the sample size can be chosen before the pixels are
 * allocated, which is what keeps a 4K wallpaper from being decoded at full resolution.
 */
actual suspend fun decodeImagePixels(path: String, maxSize: Int): ImageSamples? =
    withContext(ioDispatcher) {
        runCatching {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSize) sample *= 2
            val bitmap = android.graphics.BitmapFactory.decodeFile(
                path,
                android.graphics.BitmapFactory.Options().apply { inSampleSize = sample },
            ) ?: return@runCatching null

            val scale = minOf(1f, maxSize.toFloat() / maxOf(bitmap.width, bitmap.height))
            val sw = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val sh = (bitmap.height * scale).toInt().coerceAtLeast(1)
            val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, sw, sh, true)
            try {
                val pixels = IntArray(sw * sh)
                scaled.getPixels(pixels, 0, sw, 0, 0, sw, sh)
                ImageSamples(pixels, sw, sh)
            } finally {
                if (scaled !== bitmap) scaled.recycle()
                bitmap.recycle()
            }
        }.getOrNull()
    }

/** Never reached: [supportsWallpaperTheme] is false, so the settings sheet hides the button. */
actual suspend fun loadWallpaperPixels(maxSize: Int): ImageSamples? = null

actual fun platformPathOf(file: PlatformFile): String = file.uriToRealPath() ?: file.absolutePath()

/**
 * The Android top bar: the shared title row plus the accent strip, and nothing else.
 *
 * The desktop-only window operations (minimize, maximize, close) and the draggable area stay in
 * `desktopMain`; on Android the system owns the window, and a drag handle here would fight the
 * status-bar inset. Settings and the console toggle are ordinary chrome and are kept.
 */
@Composable
fun AndroidTitleBar(
    consoleVisible: Boolean,
    onOpenSettings: () -> Unit,
    onToggleConsole: () -> Unit,
    rainbowAccent: Boolean = false,
    onOpenProjectPage: () -> Unit = {},
    totalTokenConsume: () -> Long = { 0L },
    lastTokenConsume: () -> Int = { 0 },
    modifier: Modifier = Modifier,
) {
    // Edge to edge: the surface colour paints behind the status bar (the background comes first, so
    // it covers the padded area) while the title row is pushed clear of it.
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        AppTitleBar(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(start = 16.dp),
            consoleVisible = consoleVisible,
            onOpenSettings = onOpenSettings,
            onToggleConsole = onToggleConsole,
            onOpenProjectPage = onOpenProjectPage,
            totalTokenConsume = totalTokenConsume,
            lastTokenConsume = lastTokenConsume,
        )
        AppTitleBarAccent(rainbowAccent)
    }
}
