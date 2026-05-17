package mct.util.aio

import okio.ByteString
import okio.Options
import okio.TypedOptions

/**
 * A suspend equivalent of [okio.BufferedSource].
 *
 * Mirrors the full Okio `BufferedSource` API — every method that reads or
 * closes is `suspend`; pure query methods ([exhausted], [buffer], [timeout],
 * [rangeEquals]) remain synchronous.
 */
interface AsyncBufferedSource : AsyncSource {

    // ---- query -----------------------------------------------------------

    /** Returns true if there are no more bytes in this buffer. */
    fun exhausted(): Boolean

    /** Returns this source's internal buffer. */
    fun buffer(): AsyncBuffer

    // ---- requirements ----------------------------------------------------

    /** Suspends until [byteCount] bytes are available to read. */
    suspend fun require(byteCount: Long)

    /** Suspends until [byteCount] bytes are available, or returns false if EOF. */
    suspend fun request(byteCount: Long): Boolean

    // ---- read primitives --------------------------------------------------

    suspend fun readByte(): Byte
    suspend fun readShort(): Short
    suspend fun readShortLe(): Short
    suspend fun readInt(): Int
    suspend fun readIntLe(): Int
    suspend fun readLong(): Long
    suspend fun readLongLe(): Long
    suspend fun readDecimalLong(): Long
    suspend fun readHexadecimalUnsignedLong(): Long

    // ---- skip -------------------------------------------------------------

    /** Reads and discards [byteCount] bytes. Throws EOFException if exhausted before [byteCount] bytes. */
    suspend fun skip(byteCount: Long)

    // ---- read strings -----------------------------------------------------

    suspend fun readUtf8(): String
    suspend fun readUtf8(byteCount: Long): String
    suspend fun readUtf8Line(): String?
    suspend fun readUtf8LineStrict(): String
    suspend fun readUtf8LineStrict(limit: Long): String

    /** Removes and returns a single UTF-8 code point, reading between 1 and 4 bytes. */
    suspend fun readUtf8CodePoint(): Int

    // ---- read ByteString --------------------------------------------------

    suspend fun readByteString(): ByteString
    suspend fun readByteString(byteCount: Long): ByteString

    // ---- bulk reads -------------------------------------------------------

    /** Removes all bytes from this source and returns them as a byte array. */
    suspend fun readByteArray(): ByteArray

    /** Removes [byteCount] bytes from this source and returns them as a byte array. */
    suspend fun readByteArray(byteCount: Long): ByteArray

    /** Removes up to [sink].size bytes from this and copies them into [sink]. Returns bytes read, or -1 if exhausted. */
    suspend fun read(sink: ByteArray): Int

    suspend fun readFully(sink: AsyncBuffer, byteCount: Long)
    suspend fun readFully(sink: ByteArray)
    suspend fun readInto(array: ByteArray, offset: Int = 0, byteCount: Int = array.size - offset): Int
    suspend fun readAll(sink: AsyncSink): Long

    // ---- select -----------------------------------------------------------

    suspend fun select(options: Options): Int
    suspend fun <T : Any> select(options: TypedOptions<T>): T?

    // ---- indexOf ----------------------------------------------------------

    suspend fun indexOf(byte: Byte): Long
    suspend fun indexOf(byte: Byte, fromIndex: Long): Long
    suspend fun indexOf(byte: Byte, fromIndex: Long, toIndex: Long): Long
    suspend fun indexOf(bytes: ByteString): Long
    suspend fun indexOf(bytes: ByteString, fromIndex: Long): Long
    suspend fun indexOf(bytes: ByteString, fromIndex: Long, toIndex: Long): Long
    suspend fun indexOfElement(targetBytes: ByteString): Long
    suspend fun indexOfElement(targetBytes: ByteString, fromIndex: Long): Long

    // ---- rangeEquals ------------------------------------------------------

    fun rangeEquals(offset: Long, bytes: ByteString): Boolean
    fun rangeEquals(offset: Long, bytes: ByteString, bytesOffset: Int, byteCount: Int): Boolean

    // ---- peek -------------------------------------------------------------

    fun peek(): AsyncBufferedSource

    // --- inherited from AsyncSource ------------------------------------------

    override suspend fun read(sink: okio.Buffer, byteCount: Long): Long
    override suspend fun close()
}
