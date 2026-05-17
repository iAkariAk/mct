@file:Suppress("ObjectPropertyName")

package mct.util.io

import korlibs.io.file.std.openAsZip
import korlibs.io.stream.openAsync
import mct.util.aio.AsyncFileSystem
import mct.util.aio.VfsFileAsyncFileSystem
import mct.util.aio.ext.AsyncRelativeFileSystem
import mct.util.aio.use
import okio.Path
import okio.Path.Companion.toPath
import mct.util.aio.ext.openZipReadOnly as extOpenZipReadOnly
import mct.util.aio.ext.openZipReadWrite as extOpenZipReadWrite

private val _ROOT = "/".toPath()

val Path.Companion.ROOT: Path
    get() = _ROOT

// aka. nameWithoutExtension
val Path.filename get() = segments.lastOrNull()
val Path.stem get() = name.substringBeforeLast(".")
val Path.extension get() = name.substringAfterLast(".")
fun Path.startsWith(prefix: String) = name.startsWith(prefix)
fun Path.endsWith(suffix: String) = name.endsWith(suffix)

// ── Text IO ───────────────────────────────────────────────────────

suspend fun Path.readText(fs: AsyncFileSystem) = fs.read(this) { readUtf8() }

context(fs: AsyncFileSystem)
suspend fun Path.readText() = fs.read(this) { readUtf8() }

suspend fun Path.writeText(content: String, fs: AsyncFileSystem) = fs.write(this) { writeUtf8(content) }

context(fs: AsyncFileSystem)
suspend fun Path.writeText(content: String) = fs.write(this) { writeUtf8(content) }

// ── Zip ───────────────────────────────────────────────────────────

suspend fun AsyncFileSystem.openZipReadOnly(path: Path): AsyncFileSystem =
    extOpenZipReadOnly(path)

suspend fun AsyncFileSystem.openZipReadWrite(path: Path): AsyncFileSystem =
    extOpenZipReadWrite(path)

suspend fun ByteArray.openZipReadWrite(): AsyncFileSystem {
    val zipRoot = openAsync().openAsZip()
    return VfsFileAsyncFileSystem(zipRoot)
}

// ── Relative FS ───────────────────────────────────────────────────

fun AsyncFileSystem.newRelativeFS(dir: Path) = AsyncRelativeFileSystem(dir, this)

// ── Scoped use ────────────────────────────────────────────────────

suspend inline fun <T : mct.util.aio.AsyncCloseable, R> T.useAsync(block: (T) -> R): R = use(block)
