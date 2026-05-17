@file:Suppress("unused")

package mct.util.aio.ext

import mct.util.aio.*
import okio.FileMetadata
import okio.Path
import okio.Path.Companion.toPath

/**
 * An [AsyncFileSystem] that resolves all paths relative to [baseDir].
 *
 * This is the async equivalent of [mct.util.io.RelativeFileSystem].
 * Useful for sandboxing a subtree of a larger filesystem or for working
 * with relative paths in a scoped context.
 *
 * Example:
 * ```
 * val scoped = AsyncRelativeFileSystem("/project/src", systemFs)
 * scoped.list(".") // lists /project/src/ entries
 * ```
 */
class AsyncRelativeFileSystem(
    val baseDir: Path,
    delegate: AsyncFileSystem,
) : AsyncForwardingFileSystem(delegate) {

    /** Transforms [path] relative to [baseDir]. */
    private suspend fun resolve(path: Path): Path {
        val normalized = canonicalize(path)
        return baseDir / normalized
    }

    override suspend fun canonicalize(path: Path): Path {
        val parts = mutableListOf<String>()
        for (p in path.segments) {
            when (p) {
                "", "." -> {}
                ".." -> if (parts.isNotEmpty()) parts.removeLast()
                else -> parts += p
            }
        }
        return parts.joinToString("/").toPath()
    }

    override suspend fun metadataOrNull(path: Path): FileMetadata? =
        delegate.metadataOrNull(resolve(path))

    override suspend fun list(dir: Path): List<Path> {
        val base = resolve(dir)
        return delegate.list(base).map { it.relativeTo(baseDir) }
    }

    override suspend fun openReadOnly(file: Path): AsyncFileHandle =
        delegate.openReadOnly(resolve(file))

    override suspend fun openReadWrite(
        file: Path,
        mustCreate: Boolean,
        mustExist: Boolean,
    ): AsyncFileHandle =
        delegate.openReadWrite(resolve(file), mustCreate, mustExist)

    override suspend fun source(file: Path): AsyncSource =
        delegate.source(resolve(file))

    override suspend fun sink(file: Path, mustCreate: Boolean): AsyncSink =
        delegate.sink(resolve(file), mustCreate)

    override suspend fun appendingSink(file: Path, mustExist: Boolean): AsyncSink =
        delegate.appendingSink(resolve(file), mustExist)

    override suspend fun createDirectory(dir: Path, mustCreate: Boolean) =
        delegate.createDirectory(resolve(dir), mustCreate)

    override suspend fun delete(path: Path, mustExist: Boolean) =
        delegate.delete(resolve(path), mustExist)

    override suspend fun copy(source: Path, target: Path) =
        delegate.copy(resolve(source), resolve(target))

    override suspend fun atomicMove(source: Path, target: Path) =
        delegate.atomicMove(resolve(source), resolve(target))

    override suspend fun createSymlink(source: Path, target: Path) =
        delegate.createSymlink(resolve(source), resolve(target))
}
