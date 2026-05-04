package mct.util.io

import korlibs.io.file.VfsFile
import korlibs.io.file.baseName
import korlibs.io.file.readAsSyncStream
import korlibs.io.file.std.MemoryVfs
import korlibs.io.file.std.createZipFromTreeTo
import korlibs.io.file.std.openAsZip
import korlibs.io.lang.unsupported
import korlibs.io.stream.openAsync
import korlibs.io.stream.toAsyncStream
import okio.BufferedSource
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

internal const val BUFFER_SIZE = 0x20000 // 128KiB

@Suppress("ObjectPropertyName")
private val _ROOT = "/".toPath()

val Path.Companion.ROOT: Path
    get() = _ROOT

// aka. nameWithoutExtension
val Path.filename get() = segments.lastOrNull()
val Path.stem get() = name.substringBeforeLast(".")
val Path.extension get() = name.substringAfterLast(".")
fun Path.startsWith(prefix: String) = name.endsWith(prefix)
fun Path.endsWith(suffix: String) = name.endsWith(suffix)

inline fun Path.readText(fs: FileSystem) = fs.read(this, BufferedSource::readUtf8)

context(fs: FileSystem)
inline fun Path.readText() = readText(fs)

inline fun Path.writeText(content: String, fs: FileSystem) = fs.write(this) {
    writeUtf8(content)
}

context(fs: FileSystem)
inline fun Path.writeText(content: String) = writeText(content, fs)

suspend fun FileSystem.openZipReadOnly(path: Path): ZipFileSystem {
    val tfs = FakeFileSystem()
    val handle = openReadOnly(path)
    val astream = handle.asAsyncStreamBase().toAsyncStream()
    val zipVfs = astream.openAsZip()
    zipVfs.copyToRecursively(tfs, Path.ROOT)

    return object : ZipFileSystem(tfs) {
        override suspend fun closeAsync() {}
    }
}

suspend fun FileSystem.openZipReadWrite(path: Path): ZipFileSystem {
    val rfs = this
    val tfs = FakeFileSystem()
    val handle = openReadWrite(path)
    val astream = handle.asAsyncStreamBase().toAsyncStream()
    val zipVfs = astream.openAsZip()
    zipVfs.copyToRecursively(tfs, Path.ROOT)

    return object : ZipFileSystem(tfs) {
        override suspend fun closeAsync() {
            context(tfs) {
                val mem = MemoryVfs()
                Path.ROOT.copyToRecursively(mem)
                rfs.write(path) {
                    mem.createZipFromTreeTo(asAsyncOutputStream().toAsyncStream())
                }
            }

            tfs.close()
        }
    }
}

// Note: No way to get the file which was modified
suspend fun ByteArray.openZipReadWrite(): ZipFileSystem {
    val tfs = FakeFileSystem()
    val zipVfs = openAsync().openAsZip()
    zipVfs.copyToRecursively(tfs, Path.ROOT)

    return object : ZipFileSystem(tfs) {
        override suspend fun closeAsync() {
        }
    }
}

suspend inline fun <R, FS : FileSystem> FS.useAsync(block: (FS) -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    try {
        return block(this)
    } finally {
        if (this is ZipFileSystem)
            closeAsync()
        else close()
    }
}


abstract class ZipFileSystem(delegate: FileSystem) : DelegatedFileSystem(delegate) {
    abstract suspend fun closeAsync()

    override fun close() = unsupported("Replace with `closeAsync`")
}


private suspend fun VfsFile.copyToRecursively(
    fs: FileSystem,
    target: Path,
) {
    if (isDirectory()) {
        fs.createDirectories(target, false)
        list().collect {
            it.copyToRecursively(fs, target / it.baseName)
        }
    } else {
        readAsSyncStream().use { stream ->
            fs.write(target) {
                val buffer = ByteArray(BUFFER_SIZE)
                var len = 0
                while (stream.read(buffer, 0, BUFFER_SIZE).also { len = it } > 0) {
                    write(buffer, 0, len)
                }
            }
        }
    }
}

context(fs: FileSystem)
private suspend fun Path.copyToRecursively(
    target: VfsFile,
) {
    if (fs.metadata(this).isDirectory) {
        target.mkdirs()
        fs.list(this).forEach {
            it.copyToRecursively(target[it.name])
        }
    } else {
        fs.read(this) {
            target.writeStream(asAsyncInputStream())
        }
    }
}
