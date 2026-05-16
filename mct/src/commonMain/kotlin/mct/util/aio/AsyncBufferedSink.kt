package mct.util.aio

import okio.ByteString

/**
 * A suspend equivalent of [okio.BufferedSink].
 *
 * Mirrors the full Okio `BufferedSink` API — every method that writes or
 * flushes is `suspend`; pure query methods ([buffer], [timeout]) remain
 * synchronous.
 */
interface AsyncBufferedSink : AsyncSink {

    // ---- query -----------------------------------------------------------

    /** Returns this sink's internal buffer. */
    fun buffer(): AsyncBuffer

    // ---- emit / flush -----------------------------------------------------

    /** Writes buffered bytes to the underlying sink and returns immediately. */
    suspend fun emit(): AsyncBufferedSink

    /** Writes complete segments to the underlying sink. */
    suspend fun emitCompleteSegments(): AsyncBufferedSink

    // ---- write primitives -------------------------------------------------

    suspend fun writeByte(b: Int): AsyncBufferedSink
    suspend fun writeShort(s: Int): AsyncBufferedSink
    suspend fun writeShortLe(s: Int): AsyncBufferedSink
    suspend fun writeInt(i: Int): AsyncBufferedSink
    suspend fun writeIntLe(i: Int): AsyncBufferedSink
    suspend fun writeLong(l: Long): AsyncBufferedSink
    suspend fun writeLongLe(l: Long): AsyncBufferedSink
    suspend fun writeDecimalLong(v: Long): AsyncBufferedSink
    suspend fun writeHexadecimalUnsignedLong(v: Long): AsyncBufferedSink

    // ---- write strings ----------------------------------------------------

    suspend fun writeUtf8(string: String): AsyncBufferedSink
    suspend fun writeUtf8(string: String, beginIndex: Int, endIndex: Int): AsyncBufferedSink
    suspend fun writeUtf8CodePoint(codePoint: Int): AsyncBufferedSink

    // ---- write ByteString -------------------------------------------------

    suspend fun writeByteString(byteString: ByteString): AsyncBufferedSink

    // ---- bulk writes ------------------------------------------------------

    suspend fun write(source: okio.ByteString): AsyncBufferedSink
    suspend fun writeAll(source: AsyncSource): Long
    suspend fun write(source: ByteArray): AsyncBufferedSink
    suspend fun write(source: ByteArray, offset: Int = 0, byteCount: Int = source.size - offset): AsyncBufferedSink

    // --- inherited from AsyncSink --------------------------------------------

    override suspend fun write(source: okio.Buffer, byteCount: Long)
    override suspend fun flush()
    override suspend fun close()
}
