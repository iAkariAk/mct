package mct.util.aio

import okio.Buffer
import okio.Timeout

/**
 * An [AsyncSink] which forwards calls to another, useful for subclassing.
 *
 * This is the async equivalent of Okio's [okio.ForwardingSink].
 */
abstract class AsyncForwardingSink(
    /** [AsyncSink] to which this instance is delegating. */
    val delegate: AsyncSink,
) : AsyncSink {

    override suspend fun write(source: Buffer, byteCount: Long) =
        delegate.write(source, byteCount)

    override suspend fun flush() = delegate.flush()

    override fun timeout(): Timeout = delegate.timeout()

    override suspend fun close() = delegate.close()
}
