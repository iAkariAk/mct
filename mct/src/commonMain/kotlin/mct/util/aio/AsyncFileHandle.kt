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
) : AsyncCloseable {

    /** Backend abstraction for [AsyncFileHandle] operations. */
    internal interface Delegate {
        suspend fun read(position: Long, array: ByteArray, offset: Int, byteCount: Int): Int
        suspend fun write(position: Long, array: ByteArray, offset: Int, byteCount: Int)
        suspend fun size(): Long
        suspend fun resize(length: Long)
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
        delegate.write(position, array, offset, byteCount)
    }

    /** Returns the current size of this file in bytes. */
    suspend fun size(): Long {
        check(!_closed) { "closed" }
        return delegate.size()
    }

    /** Sets this file's length to [length], truncating or zero-filling as needed. */
    suspend fun resize(length: Long) {
        check(!_closed) { "closed" }
        delegate.resize(length)
    }

    /** Closes this handle. Subsequent operations fail. */
    override suspend fun close() {
        if (!_closed) {
            _closed = true
            delegate.close()
        }
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
    override suspend fun close() = streamBase.close()
}
