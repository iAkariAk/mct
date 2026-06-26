package mct.util.io

import okio.*

open class DelegatedFileSystem(private val delegate: FileSystem) : FileSystem() {
    override fun canonicalize(path: Path): Path = delegate.canonicalize(path)

    override fun metadataOrNull(path: Path): FileMetadata? = delegate.metadataOrNull(path)

    override fun copy(source: Path, target: Path) = delegate.copy(source, target)

    override fun deleteRecursively(fileOrDirectory: Path, mustExist: Boolean) =
        delegate.deleteRecursively(fileOrDirectory, mustExist)

    override fun listRecursively(dir: Path, followSymlinks: Boolean): Sequence<Path> =
        delegate.listRecursively(dir, followSymlinks)

    override fun list(dir: Path): List<Path> = delegate.list(dir)

    override fun listOrNull(dir: Path): List<Path>? = delegate.listOrNull(dir)

    override fun openReadOnly(file: Path): FileHandle = delegate.openReadOnly(file)

    override fun openReadWrite(
        file: Path,
        mustCreate: Boolean,
        mustExist: Boolean,
    ): FileHandle = delegate.openReadWrite(file, mustCreate, mustExist)

    override fun source(file: Path): Source = delegate.source(file)

    override fun sink(file: Path, mustCreate: Boolean): Sink = delegate.sink(file, mustCreate)

    override fun appendingSink(file: Path, mustExist: Boolean): Sink = delegate.appendingSink(file, mustExist)

    override fun createDirectory(dir: Path, mustCreate: Boolean) = delegate.createDirectories(dir, mustCreate)

    override fun atomicMove(source: Path, target: Path) = delegate.atomicMove(source, target)

    override fun delete(path: Path, mustExist: Boolean) = delegate.delete(path, mustExist)

    override fun createSymlink(source: Path, target: Path) = delegate.createSymlink(source, target)

    override fun close() = delegate.close()

}