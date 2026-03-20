package mct.util.io

import okio.*
import okio.Path.Companion.toPath

inline fun FileSystem.newRelativeFS(dir: Path) = RelativeFileSystem(dir, this)

class RelativeFileSystem(
    val baseDir: Path,
    private val delegate: FileSystem
) : FileSystem() {
    private fun resolve(path: Path): Path {
        val normalized = canonicalize(path)
        return baseDir / normalized
    }

    override fun canonicalize(path: Path): Path {
        val parts = mutableListOf<String>()

        for (p in path.segments) {
            when (p) {
                "", "." -> Unit
                ".." -> {
                    if (parts.isNotEmpty()) {
                        parts.removeLast()
                    }
                }

                else -> parts += p
            }
        }

        return parts.joinToString("/").toPath()
    }

    override fun metadataOrNull(path: Path): FileMetadata? {
        return delegate.metadataOrNull(resolve(path))
    }

    override fun list(dir: Path): List<Path> {
        val base = resolve(dir)
        return delegate.list(base).map { it.relativeTo(baseDir) }
    }

    override fun listOrNull(dir: Path): List<Path>? {
        val base = resolve(dir)
        return delegate.listOrNull(base)?.map { it.relativeTo(baseDir) }
    }

    override fun openReadOnly(file: Path): FileHandle {
        return delegate.openReadOnly(resolve(file))
    }

    override fun openReadWrite(
        file: Path,
        mustCreate: Boolean,
        mustExist: Boolean
    ): FileHandle {
        return delegate.openReadWrite(resolve(file), mustCreate, mustExist)
    }

    override fun source(file: Path): Source {
        return delegate.source(resolve(file))
    }

    override fun sink(file: Path, mustCreate: Boolean): Sink {
        return delegate.sink(resolve(file), mustCreate)
    }

    override fun appendingSink(file: Path, mustExist: Boolean): Sink {
        return delegate.appendingSink(resolve(file), mustExist)
    }

    override fun createDirectory(dir: Path, mustCreate: Boolean) {
        delegate.createDirectory(resolve(dir), mustCreate)
    }

    override fun atomicMove(source: Path, target: Path) {
        delegate.atomicMove(resolve(source), resolve(target))
    }

    override fun delete(path: Path, mustExist: Boolean) {
        delegate.delete(resolve(path), mustExist)
    }

    override fun createSymlink(source: Path, target: Path) {
        delegate.createSymlink(resolve(source), resolve(target))
    }
}
