package mct.util.aio

import okio.Buffer
import okio.Timeout

/**
 * An [AsyncSource] which forwards calls to another, useful for subclassing.
 *
 * This is the async equivalent of Okio's [okio.ForwardingSource].
 */
abstract class AsyncForwardingSource(
    /** [AsyncSource] to which this instance is delegating. */
    val delegate: AsyncSource,
) : AsyncSource {

    override suspend fun read(sink: Buffer, byteCount: Long): Long =
        delegate.read(sink, byteCount)

    override fun timeout(): Timeout = delegate.timeout()

    override suspend fun close() = delegate.close()
}
