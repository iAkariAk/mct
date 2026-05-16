package mct.util.aio

import korlibs.io.file.baseName
import kotlinx.coroutines.flow.toList
import mct.util.io.ROOT
import okio.FileMetadata
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath

/**
 * An [AsyncFileSystem] backed by a korlibs [VfsFile] root directory.
 *
 * Supports both read-only and read-write modes. In read-write mode,
 * modifications are tracked in a memory overlay and can be persisted
 * via [close] (if a flush callback is provided).
 */
class VfsFileAsyncFileSystem(
    private val root: korlibs.io.file.VfsFile,
    private val onClose: (suspend (VfsFileAsyncFileSystem) -> Unit)? = null,
) : AsyncFileSystem() {

    /** Write overlay: path → content for modified/new files. */
    private val dirty = mutableMapOf<Path, ByteArray>()

    /** Directories explicitly created (for listing). */
    private val dirs = mutableSetOf<Path>()

    private suspend fun resolve(path: Path): korlibs.io.file.VfsFile? {
        if (path.segments.isEmpty()) return root
        var current = root
        for (seg in path.segments) {
            current = current[seg]
            if (!current.exists()) return null
        }
        return current
    }

    private fun hasWrite(path: Path): Boolean = path in dirty

    override suspend fun canonicalize(path: Path): Path = path

    override suspend fun metadataOrNull(path: Path): FileMetadata? {
        if (hasWrite(path)) {
            val data = dirty[path]!!
            return FileMetadata(
                isRegularFile = true,
                isDirectory = false,
                size = data.size.toLong(),
                createdAtMillis = null,
                lastModifiedAtMillis = null,
                lastAccessedAtMillis = null,
            )
        }
        if (path in dirs) {
            return FileMetadata(
                isRegularFile = false,
                isDirectory = true,
                size = 0L,
                createdAtMillis = null,
                lastModifiedAtMillis = null,
                lastAccessedAtMillis = null,
            )
        }
        val vf = resolve(path) ?: return null
        if (!vf.exists()) return null
        val stat = vf.stat()
        return FileMetadata(
            isRegularFile = !stat.isDirectory,
            isDirectory = stat.isDirectory,
            size = stat.size,
            createdAtMillis = null,
            lastModifiedAtMillis = null,
            lastAccessedAtMillis = null,
        )
    }

    override suspend fun list(dir: Path): List<Path> {
        val items = mutableSetOf<Path>()
        // List from overlay first
        for (p in dirty.keys + dirs) {
            if (p.parent == dir) items.add(p.name.toPath())
        }
        // Then from the zip root
        val vf = resolve(dir)
        if (vf != null && vf.isDirectory()) {
            vf.list().toList().map { it.baseName.toPath() }.let(items::addAll)
        }
        return items.toList()
    }

    override suspend fun openReadOnly(file: Path): AsyncFileHandle {
        if (hasWrite(file)) return openOverlay(file)
        val vf = resolve(file) ?: throw IOException("no such file: $file")
        val data = vf.readBytes()
        return readOnlyHandle(data)
    }

    override suspend fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean): AsyncFileHandle {
        if (mustExist) {
            if (!hasWrite(file)) {
                val vf = resolve(file) ?: throw IOException("no such file: $file")
                dirty[file] = vf.readBytes()
            }
        } else if (mustCreate) {
            if (hasWrite(file) || resolve(file)?.exists() == true)
                throw IOException("file already exists: $file")
            dirty[file] = ByteArray(0)
        } else if (!hasWrite(file)) {
            val vf = resolve(file)
            dirty[file] = vf?.readBytes() ?: ByteArray(0)
        }
        return readWriteHandle(file)
    }

    override suspend fun source(file: Path): AsyncSource {
        if (hasWrite(file)) {
            val data = dirty[file]!!
            val buf = okio.Buffer().write(data)
            return object : AsyncSource {
                override suspend fun read(sink: okio.Buffer, byteCount: Long): Long = buf.read(sink, byteCount)
                override fun timeout() = okio.Timeout.NONE
                override suspend fun close() = Unit
            }
        }
        val vf = resolve(file) ?: throw IOException("no such file: $file")
        val data = vf.readBytes()
        val buf = okio.Buffer().write(data)
        return object : AsyncSource {
            override suspend fun read(sink: okio.Buffer, byteCount: Long): Long = buf.read(sink, byteCount)
            override fun timeout() = okio.Timeout.NONE
            override suspend fun close() = Unit
        }
    }

    override suspend fun sink(file: Path, mustCreate: Boolean): AsyncSink {
        if (!hasWrite(file)) {
            if (mustCreate && (resolve(file)?.exists() == true))
                throw IOException("file already exists: $file")
            dirty[file] = ByteArray(0)
        }
        return overlaySink(file)
    }

    override suspend fun appendingSink(file: Path, mustExist: Boolean): AsyncSink {
        if (mustExist && !hasWrite(file) && resolve(file)?.exists() != true)
            throw IOException("no such file: $file")
        if (!hasWrite(file)) {
            val vf = resolve(file)
            dirty[file] = vf?.readBytes() ?: ByteArray(0)
        }
        return overlaySink(file, append = true)
    }

    override suspend fun createDirectory(dir: Path, mustCreate: Boolean) {
        if (mustCreate && (dir in dirs || resolve(dir)?.exists() == true))
            throw IOException("already exists: $dir")
        dirs.add(dir)
    }

    override suspend fun delete(path: Path, mustExist: Boolean) {
        if (!hasWrite(path) && resolve(path)?.exists() != true) {
            if (mustExist) throw IOException("no such file: $path")
            return
        }
        dirty.remove(path)
        dirs.remove(path)
    }

    override suspend fun deleteRecursively(fileOrDirectory: Path, mustExist: Boolean) {
        val toRemove = (dirty.keys + dirs).filter { it.startsWith(fileOrDirectory) }
        toRemove.forEach { dirty.remove(it); dirs.remove(it) }
    }

    override suspend fun copy(source: Path, target: Path) {
        val data = if (hasWrite(source)) dirty[source]!!
                   else resolve(source)?.readBytes() ?: throw IOException("no such file: $source")
        dirty[target] = data
    }

    override suspend fun atomicMove(source: Path, target: Path) {
        copy(source, target)
        delete(source, mustExist = false)
    }

    override suspend fun createSymlink(source: Path, target: Path) =
        throw IOException("symlinks not supported")

    override suspend fun close() {
        onClose?.invoke(this)
    }

    // ── Private helpers ──────────────────────────────────

    private fun readOnlyHandle(data: ByteArray) = AsyncFileHandle(object : AsyncFileHandle.Delegate {
        override suspend fun read(position: Long, array: ByteArray, offset: Int, byteCount: Int): Int {
            if (position >= data.size) return -1
            val count = minOf(byteCount.toLong(), data.size - position).toInt()
            data.copyInto(array, offset, position.toInt(), position.toInt() + count)
            return count
        }
        override suspend fun write(pos: Long, arr: ByteArray, off: Int, len: Int) =
            throw IOException("read-only")
        override suspend fun size(): Long = data.size.toLong()
        override suspend fun resize(length: Long) = throw IOException("read-only")
        override suspend fun close() = Unit
    })

    private fun readWriteHandle(file: Path) = AsyncFileHandle(object : AsyncFileHandle.Delegate {
        override suspend fun read(position: Long, array: ByteArray, offset: Int, byteCount: Int): Int {
            val data = dirty[file]!!
            if (position >= data.size) return -1
            val count = minOf(byteCount.toLong(), data.size - position).toInt()
            data.copyInto(array, offset, position.toInt(), position.toInt() + count)
            return count
        }
        override suspend fun write(position: Long, array: ByteArray, offset: Int, byteCount: Int) {
            val data = dirty[file]!!
            val newSize = maxOf(data.size.toLong(), position + byteCount.toLong())
            val newData = data.copyOf(newSize.toInt())
            array.copyInto(newData, position.toInt(), offset, offset + byteCount)
            dirty[file] = newData
        }
        override suspend fun size(): Long = dirty[file]!!.size.toLong()
        override suspend fun resize(length: Long) {
            val data = dirty[file]!!
            dirty[file] = data.copyOf(length.toInt())
        }
        override suspend fun close() = Unit
    })

    private fun overlaySink(file: Path, append: Boolean = false) = object : AsyncSink {
        private val buf = okio.Buffer()

        override suspend fun write(source: okio.Buffer, byteCount: Long) {
            buf.write(source, byteCount)
        }
        override suspend fun flush() {
            val existing = if (append) (dirty[file] ?: ByteArray(0)) else ByteArray(0)
            val newData = existing + buf.readByteArray()
            dirty[file] = newData
        }
        override fun timeout() = okio.Timeout.NONE
        override suspend fun close() = flush()
    }

    private fun openOverlay(file: Path): AsyncFileHandle {
        val data = dirty[file]!!
        return AsyncFileHandle(object : AsyncFileHandle.Delegate {
            override suspend fun read(position: Long, array: ByteArray, offset: Int, byteCount: Int): Int {
                if (position >= data.size) return -1
                val count = minOf(byteCount.toLong(), data.size - position).toInt()
                data.copyInto(array, offset, position.toInt(), position.toInt() + count)
                return count
            }
            override suspend fun write(pos: Long, arr: ByteArray, off: Int, len: Int) =
                throw IOException("read-only overlay entry")
            override suspend fun size(): Long = data.size.toLong()
            override suspend fun resize(length: Long) = throw IOException("read-only overlay entry")
            override suspend fun close() = Unit
        })
    }

    /**
     * All entries: returns a snapshot of current files in the filesystem
     * (overlay + original), for use by zip serialization.
     */
    internal suspend fun allEntries(): Map<Path, ByteArray> {
        val result = linkedMapOf<Path, ByteArray>()
        suspend fun collect(dir: Path) {
            list(dir).forEach { child ->
                val fullPath = dir / child
                val meta = metadataOrNull(fullPath)
                if (meta != null && meta.isDirectory == true) {
                    collect(fullPath)
                } else if (meta != null) {
                    val data = if (hasWrite(fullPath)) dirty[fullPath]!!
                               else resolve(fullPath)?.readBytes() ?: return@forEach
                    result[fullPath] = data
                }
            }
        }
        collect(Path.ROOT)
        return result
    }

    /** Check if a path has been deleted (in overlay but exists in zip). */
    private fun Path.startsWith(parent: Path): Boolean {
        val segs = segments
        val parentSegs = parent.segments
        if (segs.size < parentSegs.size) return false
        return parentSegs.indices.all { segs[it] == parentSegs[it] }
    }
}
