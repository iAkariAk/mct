package mct.util.aio

import korlibs.io.file.Vfs
import korlibs.io.file.VfsFile
import korlibs.io.file.VfsOpenMode
import korlibs.io.file.deleteRecursively
import kotlinx.coroutines.flow.toList
import okio.FileMetadata
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath

/**
 * An [AsyncFileSystem] implementation backed by a korlibs [Vfs]
 * (by default [korlibs.io.file.std.LocalVfs]).
 *
 * IO operations use korlibs' native async streams and are truly non-blocking.
 * File-system mutation (create, delete, copy, move) uses the `VfsFile` suspend
 * API directly.
 *
 * @param vfs  the korlibs virtual file system to delegate to.
 */
open class KorlibsAsyncFileSystem(
    private val vfs: Vfs,
) : AsyncFileSystem() {

    // ---- helpers ---------------------------------------------------------

    private fun Path.toVfsFile(): VfsFile = vfs[toString()]

    private suspend fun VfsFile.toFileMetadata(): FileMetadata {
        val stat = stat()
        return FileMetadata(
            isRegularFile = stat.isFile,
            isDirectory = stat.isDirectory,
            size = stat.size,
            createdAtMillis = stat.createTime.unixMillisLong,
            lastModifiedAtMillis = stat.modifiedTime.unixMillisLong,
            lastAccessedAtMillis = null,
        )
    }

    private fun VfsFile.toOkioPath(): Path = path.toPath()

    // ---- path / metadata -------------------------------------------------

    override suspend fun canonicalize(path: Path): Path =
        path.toVfsFile().absolutePath.toPath()

    override suspend fun metadataOrNull(path: Path): FileMetadata? {
        return try {
            val vfsFile = path.toVfsFile()
            if (vfsFile.exists()) vfsFile.toFileMetadata() else null
        } catch (_: Exception) {
            null
        }
    }

    // ---- listing ---------------------------------------------------------

    override suspend fun list(dir: Path): List<Path> {
        val vfsDir = dir.toVfsFile()
        if (!vfsDir.exists() || !vfsDir.isDirectory()) {
            throw IOException("not a directory: $dir")
        }
        return vfsDir.list().toList().map { it.toOkioPath() }
    }

    override suspend fun listOrNull(dir: Path): List<Path>? {
        return try {
            list(dir)
        } catch (_: Exception) {
            null
        }
    }

    // ---- file handles ----------------------------------------------------

    override suspend fun openReadOnly(file: Path): AsyncFileHandle {
        val vfsFile = file.toVfsFile()
        if (!vfsFile.exists()) throw IOException("no such file: $file")
        return vfsFile.open(VfsOpenMode.READ).base.asAsyncFileHandle()
    }

    override suspend fun openReadWrite(
        file: Path,
        mustCreate: Boolean,
        mustExist: Boolean,
    ): AsyncFileHandle {
        require(!(mustCreate && mustExist)) { "mustCreate and mustExist are mutually exclusive" }
        val vfsFile = file.toVfsFile()
        if (mustCreate && vfsFile.exists()) throw IOException("file already exists: $file")
        if (mustExist && !vfsFile.exists()) throw IOException("no such file: $file")
        return vfsFile.open(VfsOpenMode.WRITE).base.asAsyncFileHandle()
    }

    // ---- streaming IO ----------------------------------------------------

    override suspend fun source(file: Path): AsyncSource {
        val vfsFile = file.toVfsFile()
        if (!vfsFile.exists()) throw IOException("no such file: $file")
        return vfsFile.open(VfsOpenMode.READ).asAsyncSource()
    }

    override suspend fun sink(file: Path, mustCreate: Boolean): AsyncSink {
        val vfsFile = file.toVfsFile()
        if (mustCreate && vfsFile.exists()) throw IOException("file already exists: $file")
        return vfsFile.open(VfsOpenMode.CREATE_OR_TRUNCATE).asAsyncSink()
    }

    override suspend fun appendingSink(file: Path, mustExist: Boolean): AsyncSink {
        val vfsFile = file.toVfsFile()
        if (mustExist && !vfsFile.exists()) throw IOException("no such file: $file")
        return vfsFile.open(VfsOpenMode.APPEND).asAsyncSink()
    }

    // ---- directory operations --------------------------------------------

    override suspend fun createDirectory(dir: Path, mustCreate: Boolean) {
        val vfsFile = dir.toVfsFile()
        if (mustCreate && vfsFile.exists()) throw IOException("already exists: $dir")
        vfsFile.mkdir()
    }

    override suspend fun delete(path: Path, mustExist: Boolean) {
        val vfsFile = path.toVfsFile()
        if (!vfsFile.exists()) {
            if (mustExist) throw IOException("no such file: $path")
            return
        }
        vfsFile.delete()
    }

    override suspend fun deleteRecursively(fileOrDirectory: Path, mustExist: Boolean) {
        val vfsFile = fileOrDirectory.toVfsFile()
        if (!vfsFile.exists()) {
            if (mustExist) throw IOException("no such file: $fileOrDirectory")
            return
        }
        vfsFile.deleteRecursively()
    }

    override suspend fun copy(source: Path, target: Path) {
        val srcFile = source.toVfsFile()
        val tgtFile = target.toVfsFile()
        if (!srcFile.exists()) throw IOException("no such file: $source")

        val srcStream = srcFile.open(VfsOpenMode.READ)
        try {
            val chunks = mutableListOf<ByteArray>()
            var totalSize = 0
            val tempBuf = ByteArray(DEFAULT_BUFFER_SIZE)
            var bytesRead: Int
            while (srcStream.read(tempBuf, 0, tempBuf.size).also { bytesRead = it } > 0) {
                val chunk = tempBuf.copyOf(bytesRead)
                chunks.add(chunk)
                totalSize += bytesRead
            }
            val allBytes = ByteArray(totalSize)
            var offset = 0
            for (chunk in chunks) {
                chunk.copyInto(allBytes, offset)
                offset += chunk.size
            }
            tgtFile.parent.mkdirs()
            tgtFile.writeBytes(allBytes)
        } finally {
            srcStream.close()
        }
    }

    override suspend fun atomicMove(source: Path, target: Path) {
        val srcFile = source.toVfsFile()
        val tgtFile = target.toVfsFile()
        if (!srcFile.exists()) throw IOException("no such file: $source")
        tgtFile.parent.mkdirs()
        srcFile.renameTo(target.toString())
    }

    override suspend fun createSymlink(source: Path, target: Path) {
        throw IOException("symlinks are not supported by KorlibsAsyncFileSystem")
    }

    override suspend fun close() {
        // Korlibs Vfs instances (e.g. LocalVfs) do not require closing.
    }
}
