package mct.util.aio

import okio.Buffer
import okio.ByteString
import okio.Timeout

/**
 * An [AsyncSource] that computes a hash of the full stream of bytes it has supplied.
 *
 * This is the async equivalent of Okio's [okio.HashingSource].
 */
class AsyncHashingSource private constructor(
    private val delegate: AsyncSource,
    private val hashSink: okio.HashingSink,
) : AsyncSource {

    /** Returns the hash of all bytes supplied thus far. */
    val hash: ByteString get() = hashSink.hash

    override suspend fun read(sink: Buffer, byteCount: Long): Long {
        val temp = Buffer()
        val read = delegate.read(temp, byteCount)
        if (read > 0L) {
            // Snapshot data for hashing BEFORE forwarding to caller
            val hashCopy = temp.copy()
            hashSink.write(hashCopy, read)
            sink.write(temp, read)
        }
        return read
    }

    override fun timeout(): Timeout = delegate.timeout()

    override suspend fun close() {
        delegate.close()
        hashSink.close()
    }

    companion object {
        fun md5(source: AsyncSource): AsyncHashingSource =
            AsyncHashingSource(source, okio.HashingSink.md5(okio.blackholeSink()))

        fun sha1(source: AsyncSource): AsyncHashingSource =
            AsyncHashingSource(source, okio.HashingSink.sha1(okio.blackholeSink()))

        fun sha256(source: AsyncSource): AsyncHashingSource =
            AsyncHashingSource(source, okio.HashingSink.sha256(okio.blackholeSink()))

        fun sha512(source: AsyncSource): AsyncHashingSource =
            AsyncHashingSource(source, okio.HashingSink.sha512(okio.blackholeSink()))

        fun hmacSha1(source: AsyncSource, key: ByteString): AsyncHashingSource =
            AsyncHashingSource(source, okio.HashingSink.hmacSha1(okio.blackholeSink(), key))

        fun hmacSha256(source: AsyncSource, key: ByteString): AsyncHashingSource =
            AsyncHashingSource(source, okio.HashingSink.hmacSha256(okio.blackholeSink(), key))

        fun hmacSha512(source: AsyncSource, key: ByteString): AsyncHashingSource =
            AsyncHashingSource(source, okio.HashingSink.hmacSha512(okio.blackholeSink(), key))
    }
}
