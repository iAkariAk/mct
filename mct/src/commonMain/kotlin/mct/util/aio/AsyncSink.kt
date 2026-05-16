package mct.util.aio

import okio.Buffer
import okio.Timeout

/**
 * A suspend equivalent of [okio.Sink].
 *
 * Mirrors the Okio `Sink` API exactly.
 */
interface AsyncSink : AsyncCloseable {
    /** Removes [byteCount] bytes from [source] and writes them to this sink. */
    suspend fun write(source: Buffer, byteCount: Long)

    /** Forces buffered bytes to be written to the underlying storage device. */
    suspend fun flush()

    /** Returns the timeout for this sink. Configuration-only, NOT suspend. */
    fun timeout(): Timeout

    override suspend fun close()
}

// ── Okio Sink → AsyncSink adapter ─────────────────────────────────

internal class BlockingSinkAsAsyncSink(
    private val sink: okio.Sink,
) : AsyncSink {
    override suspend fun write(source: Buffer, byteCount: Long) =
        sink.write(source, byteCount)

    override suspend fun flush() = sink.flush()

    override fun timeout(): Timeout = sink.timeout()

    override suspend fun close() = sink.close()
}

// ── korlibs AsyncOutputStream → AsyncSink adapter ─────────────────

internal fun korlibs.io.stream.AsyncOutputStream.asAsyncSink(): AsyncSink =
    AsyncOutputStreamAsAsyncSink(this)

private class AsyncOutputStreamAsAsyncSink(
    private val outputStream: korlibs.io.stream.AsyncOutputStream,
) : AsyncSink {
    private val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

    override suspend fun write(source: Buffer, byteCount: Long) {
        var remaining = byteCount
        while (remaining > 0L) {
            val toRead = remaining.coerceAtMost(buffer.size.toLong()).toInt()
            val bytesRead = source.read(buffer, 0, toRead)
            if (bytesRead == -1) break
            outputStream.write(buffer, 0, bytesRead)
            remaining -= bytesRead
        }
    }

    override suspend fun flush() {
        // AsyncOutputStream does not expose flush; the underlying
        // implementation is responsible for flushing.
    }

    override fun timeout(): Timeout = Timeout.NONE

    override suspend fun close() = outputStream.close()
}
