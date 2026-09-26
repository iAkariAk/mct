package mct.gui.platform

import io.github.vinceglb.filekit.PlatformFile
import io.ktor.client.*
import kotlinx.coroutines.CoroutineDispatcher
import okio.FileSystem
import okio.Path

/**
 * Host file system. Both platforms back it with okio's `SYSTEM`, i.e. the platform's own
 * filesystem, which is why every path the GUI handles is a real one the CLI can open too.
 */
expect val platformFileSystem: FileSystem

/**
 * Dispatcher for file and network work.
 *
 * commonMain's `Dispatchers` has no `IO` (it is a JVM/native-only property), so the GUI routes
 * background work through this seam instead of naming the platform dispatcher directly.
 */
expect val ioDispatcher: CoroutineDispatcher

/** Directory holding `api-settings.json` and `theme-settings.json`. */
expect val settingsDirectory: Path

/**
 * Base for paths the user typed as a relative one.
 *
 * Desktop resolves them against the process working directory, Android against the app's private
 * files directory: Android's process working directory is `/`, which is neither writable nor
 * meaningful to the user.
 */
expect val appWorkingDirectory: String

/** Whether "从壁纸获取" can be offered at all; Android's wallpaper API is not reliably readable. */
expect val supportsWallpaperTheme: Boolean

/** Reveal [path] in the host file manager; `false` when the platform cannot, so the caller prompts. */
expect fun revealInFileExplorer(path: String): Boolean

/** Format epoch milliseconds as a date; used by `formatElapsed` for entries older than a month. */
expect fun formatEpochDate(epochMillis: Long): String

/** Decoded image pixels with their dimensions — the input KMPalette needs. */
class ImageSamples(val pixels: IntArray, val width: Int, val height: Int)

/** Decode the image at [path], scaled to at most [maxSize] on its longest side; `null` on failure. */
expect suspend fun decodeImagePixels(path: String, maxSize: Int): ImageSamples?

/** Read the desktop wallpaper as [ImageSamples]; `null` when the platform has none. */
expect suspend fun loadWallpaperPixels(maxSize: Int): ImageSamples?

/** HTTP client for the language-file download; retry and timeout policy live in the implementation. */
expect fun createDownloadClient(): HttpClient

/** Copy [entryName] out of the zip at [zipPath] into [target]. */
expect fun copyZipEntry(zipPath: Path, entryName: String, target: Path)

/**
 * The real file system path of a file the user picked.
 *
 * Desktop dialogs already return one. Android's return `content://…` URIs, which every service in
 * this app would treat as a literal path; the Android implementation maps them back to the
 * `/storage/…` path the document lives at.
 */
expect fun platformPathOf(file: PlatformFile): String
