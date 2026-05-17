@file:Suppress("unused")

package mct.util.aio.ext

import korlibs.io.file.VfsFile
import korlibs.io.file.std.MemoryVfs
import korlibs.io.file.std.openAsZip
import korlibs.io.stream.openAsync
import mct.util.aio.AsyncBuffer
import mct.util.aio.AsyncFileSystem
import mct.util.aio.DEFAULT_BUFFER_SIZE
import mct.util.aio.VfsFileAsyncFileSystem
import mct.util.aio.use
import okio.Buffer
import okio.Path

/**
 * Opens the zip archive at [path] on this filesystem as a read-only [AsyncFileSystem].
 *
 * The returned filesystem reflects the contents of the zip archive at the moment of
 * opening. Changes to the original zip file are not reflected.
 *
 * Implementation uses korlibs-io's zip backend via [VfsFileAsyncFileSystem].
 */
suspend fun AsyncFileSystem.openZipReadOnly(path: Path): AsyncFileSystem {
    val bytes = readAllBytes(path)
    val zipRoot = bytes.openAsync().openAsZip()
    return VfsFileAsyncFileSystem(zipRoot)
}

/**
 * Opens the zip archive at [path] on this filesystem as a read-write [AsyncFileSystem].
 *
 * If [path] does not exist, an empty archive is created. Writes are buffered in
 * memory (via [VfsFileAsyncFileSystem]'s overlay) until [close] is invoked.
 */
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
        val temp = Buffer()
        while (true) {
            val read = src.read(temp, DEFAULT_BUFFER_SIZE.toLong())
            if (read == -1L) break
            buf.okioBuffer.write(temp, read)
        }
    }
    return buf.okioBuffer.readByteArray()
}

private suspend fun emptyZipRoot(): VfsFile {
    val mem = MemoryVfs()
    // Create a valid empty zip (22-byte EOCD record)
    val eocd = byteArrayOf(
        0x50, 0x4B, 0x05, 0x06, // end of central directory signature
        0x00, 0x00,             // number of this disk
        0x00, 0x00,             // disk where central directory starts
        0x00, 0x00,             // number of central directory records on this disk
        0x00, 0x00,             // total number of central directory records
        0x00, 0x00, 0x00, 0x00, // size of central directory
        0x00, 0x00, 0x00, 0x00, // offset of start of central directory
        0x00, 0x00,             // comment length
    )
    val f = mem["empty.zip"]
    f.writeBytes(eocd)
    return f.openAsZip()
}
