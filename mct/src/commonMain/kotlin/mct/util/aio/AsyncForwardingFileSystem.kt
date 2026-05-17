package mct.util.aio

import okio.FileMetadata
import okio.Path

/**
 * An [AsyncFileSystem] that forwards calls to another, intended for subclassing.
 *
 * This is the async equivalent of Okio's [okio.ForwardingFileSystem].
 *
 * Subclasses may override methods to add logging, fault injection, or path transformation.
 */
abstract class AsyncForwardingFileSystem(
    /** [AsyncFileSystem] to which this instance is delegating. */
    val delegate: AsyncFileSystem,
) : AsyncFileSystem() {

    override suspend fun canonicalize(path: Path): Path = delegate.canonicalize(path)
    override suspend fun metadataOrNull(path: Path): FileMetadata? = delegate.metadataOrNull(path)
    override suspend fun list(dir: Path): List<Path> = delegate.list(dir)
    override suspend fun openReadOnly(file: Path): AsyncFileHandle = delegate.openReadOnly(file)
    override suspend fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean): AsyncFileHandle =
        delegate.openReadWrite(file, mustCreate, mustExist)
    override suspend fun source(file: Path): AsyncSource = delegate.source(file)
    override suspend fun sink(file: Path, mustCreate: Boolean): AsyncSink = delegate.sink(file, mustCreate)
    override suspend fun appendingSink(file: Path, mustExist: Boolean): AsyncSink =
        delegate.appendingSink(file, mustExist)
    override suspend fun createDirectory(dir: Path, mustCreate: Boolean) =
        delegate.createDirectory(dir, mustCreate)
    override suspend fun delete(path: Path, mustExist: Boolean) = delegate.delete(path, mustExist)
    override suspend fun deleteRecursively(fileOrDirectory: Path, mustExist: Boolean) =
        delegate.deleteRecursively(fileOrDirectory, mustExist)
    override suspend fun copy(source: Path, target: Path) = delegate.copy(source, target)
    override suspend fun atomicMove(source: Path, target: Path) = delegate.atomicMove(source, target)
    override suspend fun createSymlink(source: Path, target: Path) = delegate.createSymlink(source, target)
    override suspend fun close() = delegate.close()
}
