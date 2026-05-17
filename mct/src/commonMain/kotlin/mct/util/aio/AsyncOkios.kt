package mct.util.aio

import okio.Path

// ── Path query helpers ───────────────────────────────────────────

val Path.filename get() = segments.lastOrNull()
val Path.stem get() = name.substringBeforeLast(".")
val Path.extension get() = name.substringAfterLast(".")

fun Path.startsWith(prefix: String) = name.startsWith(prefix)
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
