package mct.util.aio

import okio.IOException

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
    private var openStreamCount = 0

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
    fun source(fileOffset: Long = 0L): AsyncSource {
        check(!_closed) { "closed" }
        openStreamCount++
        return FileHandleAsyncSource(this, fileOffset)
    }

    /**
     * Returns the position of [source] in the file. The argument [source] must be either a source
     * produced by this file handle, or a [RealAsyncBufferedSource] that directly wraps such a source.
     * If the parameter is a [RealAsyncBufferedSource], it adjusts for buffered bytes.
     */
    @Throws(IOException::class)
    fun position(source: AsyncSource): Long {
        var src = source
        var bufferSize = 0L

        if (src is RealAsyncBufferedSource) {
            bufferSize = src.buffer().size
            src = src.source
        }

        require(src is FileHandleAsyncSource && src.fileHandle === this) {
            "source was not created by this FileHandle"
        }
        check(!src.closed) { "closed" }

        return src.position - bufferSize
    }

    /**
     * Change the position of [source] in the file to [position]. The argument [source] must be
     * either a source produced by this file handle, or a [RealAsyncBufferedSource] that directly
     * wraps such a source. If the parameter is a [RealAsyncBufferedSource], it will skip or clear
     * buffered bytes.
     */
    @Throws(IOException::class)
    fun reposition(source: AsyncSource, position: Long) {
        if (source is RealAsyncBufferedSource) {
            val fileHandleSource = source.source
            require(fileHandleSource is FileHandleAsyncSource && fileHandleSource.fileHandle === this) {
                "source was not created by this FileHandle"
            }
            check(!fileHandleSource.closed) { "closed" }

            val bufferSize = source.buffer().size
            val toSkip = position - (fileHandleSource.position - bufferSize)
            if (toSkip in 0L until bufferSize) {
                // The new position requires only a buffer change — no IO needed.
                source.buffer().okioBuffer.skip(toSkip)
            } else {
                // The new position doesn't share data with the current buffer.
                source.buffer().clear()
                fileHandleSource.position = position
            }
        } else {
            require(source is FileHandleAsyncSource && source.fileHandle === this) {
                "source was not created by this FileHandle"
            }
            check(!source.closed) { "closed" }
            source.position = position
        }
    }

    /**
     * Returns a sink that writes to this starting at [fileOffset].
     * The returned sink must be closed when it is no longer needed.
     */
    fun sink(fileOffset: Long = 0L): AsyncSink {
        check(!_closed) { "closed" }
        check(readWrite) { "file handle is read-only" }
        openStreamCount++
        return FileHandleAsyncSink(this, fileOffset)
    }

    /**
     * Returns a sink that writes to this starting at the end.
     * The returned sink must be closed when it is no longer needed.
     */
    suspend fun appendingSink(): AsyncSink {
        return sink(size())
    }

    /**
     * Returns the position of [sink] in the file. The argument [sink] must be either a sink
     * produced by this file handle, or a [RealAsyncBufferedSink] that directly wraps such a sink.
     * If the parameter is a [RealAsyncBufferedSink], it adjusts for buffered bytes.
     */
    @Throws(IOException::class)
    fun position(sink: AsyncSink): Long {
        var snk = sink
        var bufferSize = 0L

        if (snk is RealAsyncBufferedSink) {
            bufferSize = snk.buffer().size
            snk = snk.sink
        }

        require(snk is FileHandleAsyncSink && snk.fileHandle === this) {
            "sink was not created by this FileHandle"
        }
        check(!snk.closed) { "closed" }

        return snk.position + bufferSize
    }

    /**
     * Change the position of [sink] in the file to [position]. The argument [sink] must be either
     * a sink produced by this file handle, or a [RealAsyncBufferedSink] that directly wraps such a
     * sink. If the parameter is a [RealAsyncBufferedSink], it emits buffered bytes.
     */
    @Throws(IOException::class)
    suspend fun reposition(sink: AsyncSink, position: Long) {
        if (sink is RealAsyncBufferedSink) {
            val fileHandleSink = sink.sink
            require(fileHandleSink is FileHandleAsyncSink && fileHandleSink.fileHandle === this) {
                "sink was not created by this FileHandle"
            }
            check(!fileHandleSink.closed) { "closed" }

            sink.emit()
            fileHandleSink.position = position
        } else {
            require(sink is FileHandleAsyncSink && sink.fileHandle === this) {
                "sink was not created by this FileHandle"
            }
            check(!sink.closed) { "closed" }
            sink.position = position
        }
    }

    /** Closes this handle. If streams are still open, the actual close is deferred. */
    override suspend fun close() {
        if (_closed) return
        _closed = true
        if (openStreamCount != 0) return
        delegate.close()
    }

    internal suspend fun streamClosed() {
        openStreamCount--
        if (_closed && openStreamCount == 0) {
            delegate.close()
        }
    }
}

// ── Stream helpers ────────────────────────────────────────────────

internal class FileHandleAsyncSource(
    internal val fileHandle: AsyncFileHandle,
    internal var position: Long,
) : AsyncSource {
    internal var closed = false

    override suspend fun read(sink: okio.Buffer, byteCount: Long): Long {
        val temp = ByteArray(minOf(byteCount, DEFAULT_BUFFER_SIZE.toLong()).toInt())
        val bytesRead = fileHandle.read(position, temp, 0, temp.size)
        if (bytesRead == -1) return -1L
        sink.write(temp, 0, bytesRead)
        position += bytesRead
        return bytesRead.toLong()
    }

    override fun timeout() = okio.Timeout.NONE

    override suspend fun close() {
        if (closed) return
        closed = true
        fileHandle.streamClosed()
    }
}

internal class FileHandleAsyncSink(
    internal val fileHandle: AsyncFileHandle,
    internal var position: Long,
) : AsyncSink {
    internal var closed = false

    override suspend fun write(source: okio.Buffer, byteCount: Long) {
        val temp = ByteArray(minOf(byteCount, DEFAULT_BUFFER_SIZE.toLong()).toInt())
        var remaining = byteCount
        while (remaining > 0) {
            val toRead = minOf(remaining, temp.size.toLong()).toInt()
            val bytesRead = source.read(temp, 0, toRead)
            if (bytesRead == -1) break
            fileHandle.write(position, temp, 0, bytesRead)
            position += bytesRead
            remaining -= bytesRead
        }
    }

    override suspend fun flush() = fileHandle.flush()

    override fun timeout() = okio.Timeout.NONE

    override suspend fun close() {
        if (closed) return
        closed = true
        fileHandle.streamClosed()
    }
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
