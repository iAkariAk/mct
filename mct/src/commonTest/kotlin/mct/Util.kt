@file:OptIn(InternalCoroutinesApi::class)

package mct

import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.internal.SynchronizedObject
import kotlinx.coroutines.internal.synchronized
import okio.*

fun FileSystem.locked(): FileSystem = LockedFileSystem(this)

private class LockedFileSystem(private val delegate: FileSystem) : FileSystem() {
    private val mark = SynchronizedObject()
    override fun canonicalize(path: Path): Path = synchronized(mark) { delegate.canonicalize(path) }

    override fun metadataOrNull(path: Path): FileMetadata? = synchronized(mark) { delegate.metadataOrNull(path) }

    override fun copy(source: Path, target: Path) = synchronized(mark) { delegate.copy(source, target) }

    override fun deleteRecursively(fileOrDirectory: Path, mustExist: Boolean) =
        synchronized(mark) { delegate.deleteRecursively(fileOrDirectory, mustExist) }

    override fun listRecursively(dir: Path, followSymlinks: Boolean): Sequence<Path> =
        synchronized(mark) { delegate.listRecursively(dir, followSymlinks) }

    override fun list(dir: Path): List<Path> = synchronized(mark) { delegate.list(dir) }

    override fun listOrNull(dir: Path): List<Path>? = synchronized(mark) { delegate.listOrNull(dir) }

    override fun openReadOnly(file: Path): FileHandle = synchronized(mark) { delegate.openReadOnly(file) }


    override fun openReadWrite(
        file: Path,
        mustCreate: Boolean,
        mustExist: Boolean,
    ): FileHandle = synchronized(mark) { delegate.openReadWrite(file, mustCreate, mustExist) }

    override fun source(file: Path): Source = synchronized(mark) { delegate.source(file) }


    override fun sink(file: Path, mustCreate: Boolean): Sink = synchronized(mark) { delegate.sink(file, mustCreate) }

    override fun appendingSink(file: Path, mustExist: Boolean): Sink =
        synchronized(mark) { delegate.appendingSink(file, mustExist) }

    override fun createDirectory(dir: Path, mustCreate: Boolean) =
        synchronized(mark) { delegate.createDirectories(dir, mustCreate) }

    override fun atomicMove(source: Path, target: Path) = synchronized(mark) { delegate.atomicMove(source, target) }

    override fun delete(path: Path, mustExist: Boolean) = synchronized(mark) { delegate.delete(path, mustExist) }

    override fun createSymlink(source: Path, target: Path) =
        synchronized(mark) { delegate.createSymlink(source, target) }

    override fun close() = synchronized(mark) { delegate.close() }

}