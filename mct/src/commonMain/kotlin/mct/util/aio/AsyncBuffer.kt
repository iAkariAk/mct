package mct.util.aio

import okio.ByteString
import okio.Options
import okio.TypedOptions

/**
 * A suspend equivalent of Okio's [okio.Buffer] — a mutable byte sequence that
 * implements both [AsyncBufferedSource] and [AsyncBufferedSink].
 *
 * Internally wraps Okio's [okio.Buffer] for the actual byte storage. All
 * operations are non-blocking (in-memory); the `suspend` modifier on every
 * method ensures API consistency with other Async types.
 */
class AsyncBuffer internal constructor(
    val okioBuffer: okio.Buffer = okio.Buffer(),
) : AsyncBufferedSource, AsyncBufferedSink {

    /** Returns the number of bytes currently in this buffer. */
    val size: Long get() = okioBuffer.size

    /** Discards all bytes in this buffer. */
    fun clear() {
        okioBuffer.clear()
    }

    /** Returns a copy of this buffer's content as a [ByteString]. */
    fun snapshot(): ByteString = okioBuffer.snapshot()

    /** Returns a copy of the first [byteCount] bytes as a [ByteString]. */
    fun snapshot(byteCount: Int): ByteString = okioBuffer.snapshot(byteCount)

    /** Returns the byte at [pos]. */
    operator fun get(pos: Long): Byte = okioBuffer[pos]

    /** Copy [byteCount] bytes from this, starting at [offset], to [out]. */
    fun copyTo(out: AsyncBuffer, offset: Long = 0, byteCount: Long): AsyncBuffer {
        okioBuffer.copyTo(out.okioBuffer, offset, byteCount)
        return this
    }

    /** Overload of [copyTo] with byteCount = size - offset. */
    fun copyTo(out: AsyncBuffer, offset: Long = 0): AsyncBuffer {
        okioBuffer.copyTo(out.okioBuffer, offset)
        return this
    }

    /**
     * Returns the number of bytes in segments that are not writable. This is the number of bytes that
     * can be flushed immediately to an underlying sink without harming throughput.
     */
    fun completeSegmentByteCount(): Long = okioBuffer.completeSegmentByteCount()

    /** Returns a deep copy of this buffer. */
    fun copy(): AsyncBuffer = AsyncBuffer(okioBuffer.copy())

    // ---- hashing ----------------------------------------------------------

    fun md5(): ByteString = okioBuffer.md5()
    fun sha1(): ByteString = okioBuffer.sha1()
    fun sha256(): ByteString = okioBuffer.sha256()
    fun sha512(): ByteString = okioBuffer.sha512()
    fun hmacSha1(key: ByteString): ByteString = okioBuffer.hmacSha1(key)
    fun hmacSha256(key: ByteString): ByteString = okioBuffer.hmacSha256(key)
    fun hmacSha512(key: ByteString): ByteString = okioBuffer.hmacSha512(key)

    // ═══════════════════════════════════════════════════════════════════
    // AsyncBufferedSource
    // ═══════════════════════════════════════════════════════════════════

    // ---- query / requirements --------------------------------------------

    override fun exhausted(): Boolean = okioBuffer.exhausted()
    override fun buffer(): AsyncBuffer = this
    override suspend fun require(byteCount: Long) = okioBuffer.require(byteCount)
    override suspend fun request(byteCount: Long): Boolean = okioBuffer.request(byteCount)

    // ---- read primitives --------------------------------------------------

    override suspend fun readByte(): Byte = okioBuffer.readByte()
    override suspend fun readShort(): Short = okioBuffer.readShort()
    override suspend fun readShortLe(): Short = okioBuffer.readShortLe()
    override suspend fun readInt(): Int = okioBuffer.readInt()
    override suspend fun readIntLe(): Int = okioBuffer.readIntLe()
    override suspend fun readLong(): Long = okioBuffer.readLong()
    override suspend fun readLongLe(): Long = okioBuffer.readLongLe()
    override suspend fun readDecimalLong(): Long = okioBuffer.readDecimalLong()
    override suspend fun readHexadecimalUnsignedLong(): Long = okioBuffer.readHexadecimalUnsignedLong()

    // ---- skip -------------------------------------------------------------

    override suspend fun skip(byteCount: Long) = okioBuffer.skip(byteCount)

    // ---- read strings -----------------------------------------------------

    override suspend fun readUtf8(): String = okioBuffer.readUtf8()
    override suspend fun readUtf8(byteCount: Long): String = okioBuffer.readUtf8(byteCount)
    override suspend fun readUtf8Line(): String? = okioBuffer.readUtf8Line()
    override suspend fun readUtf8LineStrict(): String = okioBuffer.readUtf8LineStrict()
    override suspend fun readUtf8LineStrict(limit: Long): String = okioBuffer.readUtf8LineStrict(limit)
    override suspend fun readUtf8CodePoint(): Int = okioBuffer.readUtf8CodePoint()

    // ---- read ByteString --------------------------------------------------

    override suspend fun readByteString(): ByteString = okioBuffer.readByteString()
    override suspend fun readByteString(byteCount: Long): ByteString = okioBuffer.readByteString(byteCount)

    // ---- bulk reads -------------------------------------------------------

    override suspend fun readByteArray(): ByteArray = okioBuffer.readByteArray()
    override suspend fun readByteArray(byteCount: Long): ByteArray = okioBuffer.readByteArray(byteCount)
    override suspend fun read(sink: ByteArray): Int = okioBuffer.read(sink)
    override suspend fun readFully(sink: AsyncBuffer, byteCount: Long) =
        okioBuffer.readFully(sink.okioBuffer, byteCount)

    override suspend fun readFully(sink: ByteArray) = okioBuffer.readFully(sink)
    override suspend fun readInto(array: ByteArray, offset: Int, byteCount: Int): Int =
        okioBuffer.read(array, offset, byteCount)

    override suspend fun readAll(sink: AsyncSink): Long {
        if (sink is AsyncBuffer) return okioBuffer.readAll(sink.okioBuffer)
        val total = okioBuffer.size
        writeToSink(okioBuffer, sink)
        return total
    }

    // ---- select -----------------------------------------------------------

    override suspend fun select(options: Options): Int = okioBuffer.select(options)
    override suspend fun <T : Any> select(options: TypedOptions<T>): T? = okioBuffer.select(options)

    // ---- indexOf ----------------------------------------------------------

    override suspend fun indexOf(byte: Byte): Long = okioBuffer.indexOf(byte)
    override suspend fun indexOf(byte: Byte, fromIndex: Long): Long = okioBuffer.indexOf(byte, fromIndex)
    override suspend fun indexOf(byte: Byte, fromIndex: Long, toIndex: Long): Long =
        okioBuffer.indexOf(byte, fromIndex, toIndex)

    override suspend fun indexOf(bytes: ByteString): Long = okioBuffer.indexOf(bytes)
    override suspend fun indexOf(bytes: ByteString, fromIndex: Long): Long = okioBuffer.indexOf(bytes, fromIndex)
    override suspend fun indexOf(bytes: ByteString, fromIndex: Long, toIndex: Long): Long =
        okioBuffer.indexOf(bytes, fromIndex, toIndex)

    override suspend fun indexOfElement(targetBytes: ByteString): Long = okioBuffer.indexOfElement(targetBytes)
    override suspend fun indexOfElement(targetBytes: ByteString, fromIndex: Long): Long =
        okioBuffer.indexOfElement(targetBytes, fromIndex)

    // ---- rangeEquals / peek -----------------------------------------------

    override fun rangeEquals(offset: Long, bytes: ByteString): Boolean =
        okioBuffer.rangeEquals(offset, bytes)

    override fun rangeEquals(offset: Long, bytes: ByteString, bytesOffset: Int, byteCount: Int): Boolean =
        okioBuffer.rangeEquals(offset, bytes, bytesOffset, byteCount)

    override fun peek(): AsyncBufferedSource = AsyncBuffer(okioBuffer.copy())

    // ---- AsyncSource --------------------------------------------------------

    override fun timeout() = okioBuffer.timeout()
    override suspend fun read(sink: okio.Buffer, byteCount: Long): Long =
        okioBuffer.read(sink, byteCount)

    override suspend fun close() = okioBuffer.close()

    // ═══════════════════════════════════════════════════════════════════
    // AsyncBufferedSink
    // ═══════════════════════════════════════════════════════════════════

    override suspend fun emit(): AsyncBufferedSink {
        okioBuffer.emit(); return this
    }

    override suspend fun emitCompleteSegments(): AsyncBufferedSink {
        okioBuffer.emitCompleteSegments(); return this
    }

    override suspend fun writeByte(b: Int): AsyncBufferedSink {
        okioBuffer.writeByte(b); return this
    }

    override suspend fun writeShort(s: Int): AsyncBufferedSink {
        okioBuffer.writeShort(s); return this
    }

    override suspend fun writeShortLe(s: Int): AsyncBufferedSink {
        okioBuffer.writeShortLe(s); return this
    }

    override suspend fun writeInt(i: Int): AsyncBufferedSink {
        okioBuffer.writeInt(i); return this
    }

    override suspend fun writeIntLe(i: Int): AsyncBufferedSink {
        okioBuffer.writeIntLe(i); return this
    }

    override suspend fun writeLong(l: Long): AsyncBufferedSink {
        okioBuffer.writeLong(l); return this
    }

    override suspend fun writeLongLe(l: Long): AsyncBufferedSink {
        okioBuffer.writeLongLe(l); return this
    }

    override suspend fun writeDecimalLong(v: Long): AsyncBufferedSink {
        okioBuffer.writeDecimalLong(v); return this
    }

    override suspend fun writeHexadecimalUnsignedLong(v: Long): AsyncBufferedSink {
        okioBuffer.writeHexadecimalUnsignedLong(v); return this
    }

    override suspend fun writeUtf8(string: String): AsyncBufferedSink {
        okioBuffer.writeUtf8(string); return this
    }

    override suspend fun writeUtf8(string: String, beginIndex: Int, endIndex: Int): AsyncBufferedSink {
        okioBuffer.writeUtf8(string, beginIndex, endIndex); return this
    }

    override suspend fun writeUtf8CodePoint(codePoint: Int): AsyncBufferedSink {
        okioBuffer.writeUtf8CodePoint(codePoint); return this
    }

    override suspend fun writeByteString(byteString: ByteString): AsyncBufferedSink {
        okioBuffer.write(byteString); return this
    }

    override suspend fun write(source: okio.ByteString): AsyncBufferedSink {
        okioBuffer.write(source); return this
    }

    override suspend fun write(source: okio.ByteString, offset: Int, byteCount: Int): AsyncBufferedSink {
        okioBuffer.write(source, offset, byteCount); return this
    }

    override suspend fun write(source: AsyncSource, byteCount: Long): AsyncBufferedSink {
        if (source is AsyncBuffer) {
            okioBuffer.write(source.okioBuffer, byteCount)
        } else {
            readFromSourceWithLimit(okioBuffer, source, byteCount)
        }
        return this
    }

    override suspend fun writeAll(source: AsyncSource): Long {
        if (source is AsyncBuffer) return okioBuffer.writeAll(source.okioBuffer)
        val before = okioBuffer.size
        readFromSource(okioBuffer, source)
        return okioBuffer.size - before
    }

    override suspend fun write(source: ByteArray): AsyncBufferedSink {
        okioBuffer.write(source); return this
    }

    override suspend fun write(source: ByteArray, offset: Int, byteCount: Int): AsyncBufferedSink {
        okioBuffer.write(source, offset, byteCount); return this
    }

    // ---- AsyncSink ---------------------------------------------------------

    override suspend fun write(source: okio.Buffer, byteCount: Long) = okioBuffer.write(source, byteCount)
    override suspend fun flush() = okioBuffer.flush()
}

/**
 * Drains all remaining bytes from [source] into [sink] by repeated reads.
 */
private suspend fun readFromSource(sink: okio.Buffer, source: AsyncSource) {
    val temp = okio.Buffer()
    while (true) {
        val bytesRead = source.read(temp, 8192L)
        if (bytesRead == -1L) break
        sink.write(temp, bytesRead)
    }
}

/**
 * Reads up to [byteCount] bytes from [source] into [sink].
 */
private suspend fun readFromSourceWithLimit(sink: okio.Buffer, source: AsyncSource, byteCount: Long) {
    val temp = okio.Buffer()
    var remaining = byteCount
    while (remaining > 0) {
        val toRead = minOf(remaining, 8192L)
        val bytesRead = source.read(temp, toRead)
        if (bytesRead == -1L) break
        sink.write(temp, bytesRead)
        remaining -= bytesRead
    }
}

/**
 * Writes all bytes from [source] into [sink] by repeated reads.
 */
private suspend fun writeToSink(source: okio.Buffer, sink: AsyncSink) {
    val temp = okio.Buffer()
    while (source.size > 0L) {
        val toWrite = minOf(source.size, 8192L)
        source.read(temp, toWrite)
        sink.write(temp, toWrite)
    }
}
