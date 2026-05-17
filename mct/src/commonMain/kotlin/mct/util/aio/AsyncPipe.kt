package mct.util.aio

import kotlinx.coroutines.channels.Channel
import okio.Buffer
import okio.IOException
import okio.Timeout

/**
 * An async source and sink pair. Writing to the [sink] will eventually produce data
 * that can be read from the [source]. This is the async equivalent of Okio's [okio.Pipe].
 *
 * @param maxBufferSize the maximum number of bytes buffered. Writes to [sink] will suspend
 *     until the buffer is below this limit.
 */
class AsyncPipe(
    private val maxBufferSize: Long,
) {
    // Channel capacity approximates the max number of chunks that can be buffered.
    // sink.send() suspends when the channel is full, providing backpressure.
    // chunkSize is bounded by [256, DEFAULT_BUFFER_SIZE] so the channel can hold
    // enough items to prevent deadlock on small writes.
    private val chunkSize = (maxBufferSize / 2).coerceIn(
        256,
        DEFAULT_BUFFER_SIZE.toLong(),
    )
    private val channelCapacity =
        ((maxBufferSize + chunkSize - 1) / chunkSize).toInt().coerceAtLeast(1)
    private val channel = Channel<ByteArray>(channelCapacity)

    private var sinkClosed = false
    private var sourceClosed = false

    /** Write end of the pipe. */
    val sink: AsyncSink = PipeSink()

    /** Read end of the pipe. */
    val source: AsyncSource = PipeSource()

    /**
     * Reads all remaining data from [source] and writes it to [sink].
     * Both the pipe source and [sink] will be closed when this returns.
     */
    suspend fun fold(sink: AsyncSink) {
        try {
            val buf = AsyncBuffer()
            while (true) {
                buf.okioBuffer.clear()
                val read = source.read(buf.okioBuffer, chunkSize)
                if (read == -1L) break
                sink.write(buf.okioBuffer, read)
            }
        } finally {
            sink.close()
        }
    }

    /**
     * Causes this pipe to become cancelled. Sources and sinks that are already
     * blocked on reads and writes will immediately fail with an [IOException].
     */
    fun cancel() {
        sinkClosed = true
        sourceClosed = true
        channel.close()
    }

    private inner class PipeSink : AsyncSink {
        override suspend fun write(source: Buffer, byteCount: Long) {
            if (sinkClosed) throw IOException("closed")

            var remaining = byteCount
            while (remaining > 0) {
                val toSend = minOf(remaining, chunkSize).toInt()
                val chunk = ByteArray(toSend)
                source.read(chunk, 0, toSend)
                // This will suspend if the channel buffer is full (backpressure)
                channel.send(chunk)
                remaining -= toSend
            }
        }

        override suspend fun flush() = Unit

        override fun timeout(): Timeout = Timeout.NONE

        override suspend fun close() {
            sinkClosed = true
            channel.close()
        }
    }

    private inner class PipeSource : AsyncSource {
        // Partial chunk from last read that wasn't fully consumed
        private var partial: ByteArray? = null
        private var partialOffset = 0

        override suspend fun read(sink: Buffer, byteCount: Long): Long {
            if (sourceClosed) throw IOException("closed")

            // Serve from partial chunk first
            val p = partial
            if (p != null) {
                val available = p.size - partialOffset
                val toRead = minOf(byteCount, available.toLong()).toInt()
                sink.write(p, partialOffset, toRead)
                partialOffset += toRead
                if (partialOffset >= p.size) {
                    partial = null
                    partialOffset = 0
                }
                return toRead.toLong()
            }

            // Suspend until data is available or channel is closed
            val result = channel.receiveCatching()
            if (result.isClosed) {
                return if (sinkClosed) -1L else throw IOException("cancelled")
            }
            val chunk = result.getOrThrow()
            val chunkToRead = minOf(byteCount, chunk.size.toLong()).toInt()
            sink.write(chunk, 0, chunkToRead)
            if (chunkToRead < chunk.size) {
                partial = chunk
                partialOffset = chunkToRead
            }
            return chunkToRead.toLong()
        }

        override fun timeout(): Timeout = Timeout.NONE

        override suspend fun close() {
            sourceClosed = true
            channel.close()
        }
    }
}
