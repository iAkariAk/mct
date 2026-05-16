package mct.util.aio

import korlibs.io.file.std.openAsZip
import korlibs.io.stream.openAsync
import okio.Path

// ── Path query helpers ───────────────────────────────────────────

val Path.filename get() = segments.lastOrNull()
val Path.stem get() = name.substringBeforeLast(".")
val Path.extension get() = name.substringAfterLast(".")

fun Path.startsWith(prefix: String) = name.endsWith(prefix)
fun Path.endsWith(suffix: String) = name.endsWith(suffix)

// ── Async file read/write ───────────────────────────────────────

suspend fun AsyncFileSystem.readText(path: Path): String {
    val buf = AsyncBuffer()
    source(path).use { src ->
        while (true) {
            val read = src.read(buf.okioBuffer, DEFAULT_BUFFER_SIZE.toLong())
            if (read == -1L) break
        }
    }
    return buf.readUtf8()
}

suspend fun AsyncFileSystem.writeText(path: Path, content: String) {
    sink(path).use { sink ->
        val buf = AsyncBuffer()
        buf.writeUtf8(content)
        sink.write(buf.okioBuffer, buf.size)
    }
}

// ── Zip helpers (pure korlibs Vfs) ──────────────────────────────

suspend fun AsyncFileSystem.openZipReadOnly(path: Path): AsyncFileSystem {
    val bytes = readAllBytes(path)
    val zipRoot = bytes.openAsync().openAsZip()
    return VfsFileAsyncFileSystem(zipRoot)
}

suspend fun AsyncFileSystem.openZipReadWrite(path: Path): AsyncFileSystem {
    val bytes = if (exists(path)) readAllBytes(path) else ByteArray(0)
    val zipRoot = if (bytes.isNotEmpty()) bytes.openAsync().openAsZip()
                  else emptyZipRoot()
    return VfsFileAsyncFileSystem(zipRoot)
}

// ── Internal helpers ─────────────────────────────────────────────

private suspend fun AsyncFileSystem.readAllBytes(path: Path): ByteArray {
    val buf = AsyncBuffer()
    source(path).use { src ->
        while (true) {
            val read = src.read(buf.okioBuffer, DEFAULT_BUFFER_SIZE.toLong())
            if (read == -1L) break
        }
    }
    return buf.okioBuffer.readByteArray()
}

private suspend fun emptyZipRoot(): korlibs.io.file.VfsFile {
    val mem = korlibs.io.file.std.MemoryVfs()
    val f = mem["empty.zip"]
    f.writeBytes(ByteArray(0))
    return f.openAsZip()
}
