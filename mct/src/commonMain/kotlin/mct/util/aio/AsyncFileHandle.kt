package mct.util.aio

/**
 * A suspend equivalent of Okio's [okio.FileHandle] — provides random-access
 * read/write over an open file.
 *
 * Instances are obtained via [AsyncFileSystem.openReadOnly] or
 * [AsyncFileSystem.openReadWrite].
 */
class AsyncFileHandle internal constructor(
    private val delegate: Delegate,
    /** True if this handle supports both reading and writing. */
    val readWrite: Boolean = true,
) : AsyncCloseable {

    /** Backend abstraction for [AsyncFileHandle] operations. */
    internal interface Delegate {
        suspend fun read(position: Long, array: ByteArray, offset: Int, byteCount: Int): Int
        suspend fun write(position: Long, array: ByteArray, offset: Int, byteCount: Int)
        suspend fun size(): Long
        suspend fun resize(length: Long)
        suspend fun flush()
        suspend fun close()
    }

    private var _closed = false

    /** Whether this handle is open and may be used for IO. */
    val isOpen: Boolean get() = !_closed

    /**
     * Reads up to [byteCount] bytes starting at [position] into [array]
     * at [offset]. Returns the number of bytes read, or -1 if EOF.
     */
    suspend fun read(
        position: Long,
        array: ByteArray,
        offset: Int = 0,
        byteCount: Int = array.size - offset,
    ): Int {
        check(!_closed) { "closed" }
        return delegate.read(position, array, offset, byteCount)
    }

    /**
     * Reads up to [byteCount] bytes from this starting at [fileOffset] and appends
     * them to [sink]. Returns the number of bytes read, or -1 if [fileOffset] equals the file size.
     */
    suspend fun read(fileOffset: Long, sink: AsyncBuffer, byteCount: Long): Long {
        check(!_closed) { "closed" }
        var totalRead = 0L
        var currentOffset = fileOffset
        val remaining = byteCount
        val temp = ByteArray(DEFAULT_BUFFER_SIZE.coerceAtMost(remaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()))
        while (totalRead < remaining) {
            val toRead = minOf(remaining - totalRead, temp.size.toLong()).toInt()
            val bytesRead = delegate.read(currentOffset, temp, 0, toRead)
            if (bytesRead == -1) {
                if (totalRead == 0L) return -1L
                break
            }
            sink.okioBuffer.write(temp, 0, bytesRead)
            currentOffset += bytesRead
            totalRead += bytesRead
        }
        return totalRead
    }

    /**
     * Writes [byteCount] bytes from [array] starting at [offset] to this
     * file at [position].
     */
    suspend fun write(
        position: Long,
        array: ByteArray,
        offset: Int = 0,
        byteCount: Int = array.size - offset,
    ) {
        check(!_closed) { "closed" }
        check(readWrite) { "file handle is read-only" }
        delegate.write(position, array, offset, byteCount)
    }

    /**
     * Removes [byteCount] bytes from [source] and writes them to this at [fileOffset].
     */
    suspend fun write(fileOffset: Long, source: AsyncBuffer, byteCount: Long) {
        check(!_closed) { "closed" }
        check(readWrite) { "file handle is read-only" }
        val temp = ByteArray(DEFAULT_BUFFER_SIZE)
        var remaining = byteCount
        var currentOffset = fileOffset
        while (remaining > 0) {
            val toRead = minOf(remaining, temp.size.toLong()).toInt()
            val bytesRead = source.okioBuffer.read(temp, 0, toRead)
            if (bytesRead == -1) break
            delegate.write(currentOffset, temp, 0, bytesRead)
            currentOffset += bytesRead
            remaining -= bytesRead
        }
    }

    /** Returns the current size of this file in bytes. */
    suspend fun size(): Long {
        check(!_closed) { "closed" }
        return delegate.size()
    }

    /** Sets this file's length to [length], truncating or zero-filling as needed. */
    suspend fun resize(length: Long) {
        check(!_closed) { "closed" }
        check(readWrite) { "file handle is read-only" }
        delegate.resize(length)
    }

    /** Forces buffered bytes to be written to the underlying storage device. */
    suspend fun flush() {
        check(!_closed) { "closed" }
        check(readWrite) { "file handle is read-only" }
        delegate.flush()
    }

    /**
     * Returns a source that reads from this starting at [fileOffset].
     * The returned source must be closed when it is no longer needed.
     */
    suspend fun source(fileOffset: Long = 0L): AsyncSource {
        check(!_closed) { "closed" }
        return FileHandleAsyncSource(this, fileOffset)
    }

    /**
     * Returns a sink that writes to this starting at [fileOffset].
     * The returned sink must be closed when it is no longer needed.
     */
    suspend fun sink(fileOffset: Long = 0L): AsyncSink {
        check(!_closed) { "closed" }
        check(readWrite) { "file handle is read-only" }
        return FileHandleAsyncSink(this, fileOffset)
    }

    /**
     * Returns a sink that writes to this starting at the end.
     */
    suspend fun appendingSink(): AsyncSink {
        return sink(size())
    }

    /** Closes this handle. Subsequent operations fail. */
    override suspend fun close() {
        if (!_closed) {
            _closed = true
            delegate.close()
        }
    }
}

// ── Stream helpers ────────────────────────────────────────────────

private class FileHandleAsyncSource(
    private val handle: AsyncFileHandle,
    private var position: Long,
) : AsyncSource {
    override suspend fun read(sink: okio.Buffer, byteCount: Long): Long {
        val temp = ByteArray(minOf(byteCount, DEFAULT_BUFFER_SIZE.toLong()).toInt())
        val bytesRead = handle.read(position, temp, 0, temp.size)
        if (bytesRead == -1) return -1L
        sink.write(temp, 0, bytesRead)
        position += bytesRead
        return bytesRead.toLong()
    }

    override fun timeout() = okio.Timeout.NONE

    override suspend fun close() = Unit
}

private class FileHandleAsyncSink(
    private val handle: AsyncFileHandle,
    private var position: Long,
) : AsyncSink {
    override suspend fun write(source: okio.Buffer, byteCount: Long) {
        val temp = ByteArray(minOf(byteCount, DEFAULT_BUFFER_SIZE.toLong()).toInt())
        var remaining = byteCount
        while (remaining > 0) {
            val toRead = minOf(remaining, temp.size.toLong()).toInt()
            val bytesRead = source.read(temp, 0, toRead)
            if (bytesRead == -1) break
            handle.write(position, temp, 0, bytesRead)
            position += bytesRead
            remaining -= bytesRead
        }
    }

    override suspend fun flush() = handle.flush()

    override fun timeout() = okio.Timeout.NONE

    override suspend fun close() = Unit
}

// ── Okio FileHandle → AsyncFileHandle adapter ─────────────────────

internal class BlockingFileHandleDelegate(
    private val handle: okio.FileHandle,
) : AsyncFileHandle.Delegate {
    override suspend fun read(position: Long, array: ByteArray, offset: Int, byteCount: Int): Int =
        handle.read(position, array, offset, byteCount)

    override suspend fun write(position: Long, array: ByteArray, offset: Int, byteCount: Int) =
        handle.write(position, array, offset, byteCount)

    override suspend fun size(): Long = handle.size()
    override suspend fun resize(length: Long) = handle.resize(length)
    override suspend fun flush() = handle.flush()
    override suspend fun close() = handle.close()
}

// ── korlibs AsyncStreamBase → AsyncFileHandle adapter ─────────────

internal fun korlibs.io.stream.AsyncStreamBase.asAsyncFileHandle(): AsyncFileHandle =
    AsyncFileHandle(AsyncStreamBaseDelegate(this))

private class AsyncStreamBaseDelegate(
    private val streamBase: korlibs.io.stream.AsyncStreamBase,
) : AsyncFileHandle.Delegate {
    override suspend fun read(position: Long, array: ByteArray, offset: Int, byteCount: Int): Int =
        streamBase.read(position, array, offset, byteCount)

    override suspend fun write(position: Long, array: ByteArray, offset: Int, byteCount: Int) =
        streamBase.write(position, array, offset, byteCount)

    override suspend fun size(): Long = streamBase.getLength()
    override suspend fun resize(length: Long) = streamBase.setLength(length)
    override suspend fun flush() = Unit
    override suspend fun close() = streamBase.close()
}
