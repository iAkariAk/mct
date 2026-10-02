package mct.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okio.*

/**
 * A browser has no filesystem, but this value is `Env`'s default for `fs`, and a default argument is
 * evaluated at every `Env(...)` call site — so it has to be *constructible*. Returning it and
 * failing on use turns the old `Unreachable` (thrown while building an `Env`, with no clue about
 * why) into one message naming the actual problem.
 */
private object UnsupportedWebFileSystem : FileSystem() {
    private fun unsupported(): Nothing =
        error("There is no filesystem on Kotlin/Wasm; pass an explicit FileSystem to Env")

    override fun canonicalize(path: Path): Path = unsupported()
    override fun metadataOrNull(path: Path): FileMetadata? = unsupported()
    override fun copy(source: Path, target: Path): Unit = unsupported()
    override fun deleteRecursively(fileOrDirectory: Path, mustExist: Boolean): Unit = unsupported()
    override fun listRecursively(dir: Path, followSymlinks: Boolean): Sequence<Path> = unsupported()
    override fun list(dir: Path): List<Path> = unsupported()
    override fun listOrNull(dir: Path): List<Path>? = unsupported()
    override fun openReadOnly(file: Path): FileHandle = unsupported()
    override fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean): FileHandle = unsupported()
    override fun source(file: Path): Source = unsupported()
    override fun sink(file: Path, mustCreate: Boolean): Sink = unsupported()
    override fun appendingSink(file: Path, mustExist: Boolean): Sink = unsupported()
    override fun createDirectory(dir: Path, mustCreate: Boolean): Unit = unsupported()
    override fun atomicMove(source: Path, target: Path): Unit = unsupported()
    override fun delete(path: Path, mustExist: Boolean): Unit = unsupported()
    override fun createSymlink(source: Path, target: Path): Unit = unsupported()

    /** Closing has to succeed: it runs on the way out of paths that never used the filesystem. */
    override fun close() = Unit
}

actual val SystemFileSystem: FileSystem get() = UnsupportedWebFileSystem
actual fun envvar(name: String): String? = unreachable
actual val Dispatchers.IO: CoroutineDispatcher get() = Dispatchers.Main
