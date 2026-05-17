@file:Suppress("unused")

package mct.util.aio.ext

import korlibs.io.file.std.MemoryVfs
import korlibs.io.file.std.createZipFromTreeTo
import korlibs.io.file.std.openAsZip
import korlibs.io.stream.openAsync
import mct.util.aio.AsyncFileSystem
import mct.util.aio.VfsFileAsyncFileSystem
import mct.util.aio.buffer
import mct.util.aio.use
import okio.IOException
import okio.Path

suspend fun AsyncFileSystem.openZipReadOnly(path: Path): AsyncFileSystem {
    val bytes = read(path) { readByteArray() }
    val zipRoot = bytes.openAsync().openAsZip()
    return VfsFileAsyncFileSystem(zipRoot)
}

suspend fun AsyncFileSystem.openZipReadWrite(path: Path): AsyncFileSystem {
    if (!exists(path)) throw IOException("no such file: $path")
    val bytes = read(path) { readByteArray() }
    val zipRoot = bytes.openAsync().openAsZip()
    val parent = this
    return VfsFileAsyncFileSystem(zipRoot) { vfs ->
        val mem = MemoryVfs()
        for ((entryPath, entryData) in vfs.allEntries()) {
            val f = mem[entryPath.toString()]
            f.parent.mkdirs()
            f.writeBytes(entryData)
        }
        val zipFile = mem["output.zip"]
        mem.createZipFromTreeTo(zipFile)
        val zipBytes = zipFile.readBytes()
        parent.sink(path).buffer().use { snk ->
            snk.write(zipBytes)
        }
    }
}
