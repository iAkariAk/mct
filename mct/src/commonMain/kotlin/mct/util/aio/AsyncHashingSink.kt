package mct.util.aio

import okio.Buffer
import okio.ByteString
import okio.Timeout

/**
 * An [AsyncSink] that computes a hash of the full stream of bytes it has accepted.
 *
 * This is the async equivalent of Okio's [okio.HashingSink].
 */
class AsyncHashingSink private constructor(
    private val delegate: AsyncSink,
    private val hashSink: okio.HashingSink,
) : AsyncSink {

    /** Returns the hash of all bytes processed thus far. */
    val hash: ByteString get() = hashSink.hash

    override suspend fun write(source: Buffer, byteCount: Long) {
        // Snapshot exactly byteCount bytes for hashing before forwarding
        val hashChunk = Buffer()
        source.copyTo(hashChunk, 0, byteCount)
        delegate.write(source, byteCount)
        hashSink.write(hashChunk, byteCount)
    }

    override suspend fun flush() = delegate.flush()

    override fun timeout(): Timeout = delegate.timeout()

    override suspend fun close() {
        delegate.close()
        hashSink.close()
    }

    companion object {
        fun md5(sink: AsyncSink): AsyncHashingSink =
            AsyncHashingSink(sink, okio.HashingSink.md5(okio.blackholeSink()))

        fun sha1(sink: AsyncSink): AsyncHashingSink =
            AsyncHashingSink(sink, okio.HashingSink.sha1(okio.blackholeSink()))

        fun sha256(sink: AsyncSink): AsyncHashingSink =
            AsyncHashingSink(sink, okio.HashingSink.sha256(okio.blackholeSink()))

        fun sha512(sink: AsyncSink): AsyncHashingSink =
            AsyncHashingSink(sink, okio.HashingSink.sha512(okio.blackholeSink()))

        fun hmacSha1(sink: AsyncSink, key: ByteString): AsyncHashingSink =
            AsyncHashingSink(sink, okio.HashingSink.hmacSha1(okio.blackholeSink(), key))

        fun hmacSha256(sink: AsyncSink, key: ByteString): AsyncHashingSink =
            AsyncHashingSink(sink, okio.HashingSink.hmacSha256(okio.blackholeSink(), key))

        fun hmacSha512(sink: AsyncSink, key: ByteString): AsyncHashingSink =
            AsyncHashingSink(sink, okio.HashingSink.hmacSha512(okio.blackholeSink(), key))
    }
}
