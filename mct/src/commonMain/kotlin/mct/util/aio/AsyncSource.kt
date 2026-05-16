package mct.util.aio

import okio.Buffer
import okio.Timeout

/**
 * A suspend equivalent of [okio.Source].
 *
 * Mirrors the Okio `Source` API exactly: same parameter types, same return
 * types, same semantics — the only difference is that [read] and [close] are
 * suspend functions.
 */
interface AsyncSource : AsyncCloseable {
    /**
     * Removes at least 1 byte, up to [byteCount] bytes, from this and
     * appends them to [sink]. Returns the number of bytes read, or -1 if
     * this source is exhausted.
     */
    suspend fun read(sink: Buffer, byteCount: Long): Long

    /** Returns the timeout for this source. Configuration-only, NOT suspend. */
    fun timeout(): Timeout

    override suspend fun close()
}

// ── Okio Source → AsyncSource adapter ─────────────────────────────

internal class BlockingSourceAsAsyncSource(
    private val source: okio.Source,
) : AsyncSource {
    override suspend fun read(sink: Buffer, byteCount: Long): Long =
        source.read(sink, byteCount)

    override fun timeout(): Timeout = source.timeout()

    override suspend fun close() = source.close()
}

// ── korlibs AsyncInputStream → AsyncSource adapter ────────────────

internal fun korlibs.io.stream.AsyncInputStream.asAsyncSource(): AsyncSource =
    AsyncInputStreamAsAsyncSource(this)

private class AsyncInputStreamAsAsyncSource(
    private val inputStream: korlibs.io.stream.AsyncInputStream,
) : AsyncSource {
    private val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

    override suspend fun read(sink: Buffer, byteCount: Long): Long {
        val toRead = byteCount.coerceAtMost(buffer.size.toLong()).toInt()
        val bytesRead = inputStream.read(buffer, 0, toRead)
        if (bytesRead <= 0) return -1L
        sink.write(buffer, 0, bytesRead)
        return bytesRead.toLong()
    }

    override fun timeout(): Timeout = Timeout.NONE

    override suspend fun close() = inputStream.close()
}
