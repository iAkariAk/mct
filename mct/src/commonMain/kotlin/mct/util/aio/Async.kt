@file:Suppress("unused")

package mct.util.aio

import okio.BufferedSink
import okio.BufferedSource
import okio.FileHandle
import okio.FileSystem
import okio.Sink
import okio.Source
import kotlin.Suppress
import kotlin.Throwable
import kotlin.addSuppressed
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.invoke

/** Default buffer size used by adapters when bridging between stream APIs. */
internal const val DEFAULT_BUFFER_SIZE = 8192

/**
 * A closeable resource whose [close] operation is a suspend function.
 *
 * Mirrors the role of okio's `Closeable` in the async layer. Implementors must
 * guarantee idempotent [close] — subsequent calls should be no-ops.
 */
interface AsyncCloseable {
    suspend fun close()
}

// ── Okio → Async adapters ─────────────────────────────────────────

/** Wraps this blocking [Source] into an [AsyncSource]. */
fun Source.zio(): AsyncSource = BlockingSourceAsAsyncSource(this)

/** Wraps this blocking [Sink] into an [AsyncSink]. */
fun Sink.zio(): AsyncSink = BlockingSinkAsAsyncSink(this)

/** Wraps this blocking [FileHandle] into an [AsyncFileHandle]. */
fun FileHandle.zio(): AsyncFileHandle = AsyncFileHandle(BlockingFileHandleDelegate(this))

/** Wraps this blocking [FileSystem] into an [AsyncFileSystem]. */
fun FileSystem.zio(): AsyncFileSystem = BlockingFileSystemAsAsyncFileSystem(this)

/** Wraps this blocking [BufferedSource] into an [AsyncBufferedSource]. */
fun BufferedSource.zio(): AsyncBufferedSource = AsyncBuffer(buffer() as okio.Buffer)

/** Wraps this blocking [BufferedSink] into an [AsyncBufferedSink]. */
fun BufferedSink.zio(): AsyncBufferedSink = AsyncBuffer(buffer() as okio.Buffer)

// ── use() extension ──────────────────────────────────────────────

/**
 * Executes the given [block] on this resource and then closes it,
 * whether the block completes normally or throws an exception.
 *
 * This is the suspend equivalent of `kotlin.io.use` for [AsyncCloseable].
 */
suspend inline fun <T : AsyncCloseable, R> T.use(block: (T) -> R): R {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    var exception: Throwable? = null
    try {
        return block(this)
    } catch (e: Throwable) {
        exception = e
        throw e
    } finally {
        when (exception) {
            null -> close()
            else ->
                try {
                    close()
                } catch (closeException: Throwable) {
                    exception.addSuppressed(closeException)
                }
        }
    }
}
