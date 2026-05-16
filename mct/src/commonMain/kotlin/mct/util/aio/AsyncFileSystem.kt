package mct.util.aio

import okio.FileMetadata
import okio.IOException
import okio.Path

/**
 * A suspend equivalent of Okio's [okio.FileSystem].
 *
 * Every abstract method mirrors an Okio `FileSystem` method with the same
 * signature except that all IO-bound methods are `suspend`. Non-abstract
 * methods ([metadata], [listOrNull], [listRecursively], [createDirectories])
 * provide default implementations that subclasses may override.
 */
abstract class AsyncFileSystem : AsyncCloseable {

    // ---- path / metadata -------------------------------------------------

    abstract suspend fun canonicalize(path: Path): Path
    abstract suspend fun metadataOrNull(path: Path): FileMetadata?

    suspend fun metadata(path: Path): FileMetadata {
        return metadataOrNull(path) ?: throw IOException("file not found: $path")
    }

    /** Returns true if [path] exists and is readable. */
    open suspend fun exists(path: Path): Boolean = metadataOrNull(path) != null

    // ---- listing ---------------------------------------------------------

    abstract suspend fun list(dir: Path): List<Path>

    open suspend fun listOrNull(dir: Path): List<Path>? {
        return try {
            list(dir)
        } catch (_: IOException) {
            null
        }
    }

    open suspend fun listRecursively(
        dir: Path,
        followSymlinks: Boolean = false,
    ): Sequence<Path> {
        val result = mutableListOf<Path>()
        listRecursively(dir, result)
        return result.asSequence()
    }

    private suspend fun listRecursively(dir: Path, result: MutableList<Path>) {
        val children = list(dir)
        for (child in children) {
            result.add(child)
            val meta = metadataOrNull(child)
            if (meta != null && meta.isDirectory == true) {
                listRecursively(child, result)
            }
        }
    }

    // ---- file handles ----------------------------------------------------

    abstract suspend fun openReadOnly(file: Path): AsyncFileHandle
    abstract suspend fun openReadWrite(
        file: Path,
        mustCreate: Boolean = false,
        mustExist: Boolean = false,
    ): AsyncFileHandle

    // ---- streaming IO ----------------------------------------------------

    abstract suspend fun source(file: Path): AsyncSource
    abstract suspend fun sink(file: Path, mustCreate: Boolean = false): AsyncSink
    abstract suspend fun appendingSink(file: Path, mustExist: Boolean = false): AsyncSink

    // ---- directory operations --------------------------------------------

    abstract suspend fun createDirectory(dir: Path, mustCreate: Boolean = true)

    open suspend fun createDirectories(dir: Path, mustCreate: Boolean = true) {
        val resolved = canonicalize(dir)
        val parent = resolved.parent
        if (parent != null) {
            createDirectories(parent, mustCreate = false)
        }
        createDirectory(resolved, mustCreate)
    }

    // ---- delete / copy / move --------------------------------------------

    abstract suspend fun delete(path: Path, mustExist: Boolean = true)
    abstract suspend fun deleteRecursively(fileOrDirectory: Path, mustExist: Boolean = true)
    abstract suspend fun copy(source: Path, target: Path)
    abstract suspend fun atomicMove(source: Path, target: Path)
    abstract suspend fun createSymlink(source: Path, target: Path)

    // ---- lifecycle -------------------------------------------------------

    override abstract suspend fun close()
}

// ── Okio FileSystem → AsyncFileSystem adapter ─────────────────────

internal class BlockingFileSystemAsAsyncFileSystem(
    private val fs: okio.FileSystem,
) : AsyncFileSystem() {

    override suspend fun canonicalize(path: Path): Path = fs.canonicalize(path)
    override suspend fun metadataOrNull(path: Path): FileMetadata? = fs.metadataOrNull(path)

    override suspend fun list(dir: Path): List<Path> = fs.list(dir)

    override suspend fun listOrNull(dir: Path): List<Path>? = fs.listOrNull(dir)

    override suspend fun openReadOnly(file: Path): AsyncFileHandle =
        fs.openReadOnly(file).zio()

    override suspend fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean): AsyncFileHandle =
        fs.openReadWrite(file, mustCreate, mustExist).zio()

    override suspend fun source(file: Path): AsyncSource = fs.source(file).zio()

    override suspend fun sink(file: Path, mustCreate: Boolean): AsyncSink =
        fs.sink(file, mustCreate).zio()

    override suspend fun appendingSink(file: Path, mustExist: Boolean): AsyncSink =
        fs.appendingSink(file, mustExist).zio()

    override suspend fun createDirectory(dir: Path, mustCreate: Boolean) =
        fs.createDirectory(dir, mustCreate)

    override suspend fun delete(path: Path, mustExist: Boolean) = fs.delete(path, mustExist)

    override suspend fun deleteRecursively(fileOrDirectory: Path, mustExist: Boolean) =
        fs.deleteRecursively(fileOrDirectory, mustExist)

    override suspend fun copy(source: Path, target: Path) = fs.copy(source, target)
    override suspend fun atomicMove(source: Path, target: Path) = fs.atomicMove(source, target)
    override suspend fun createSymlink(source: Path, target: Path) = fs.createSymlink(source, target)
    override suspend fun close() = fs.close()
}
