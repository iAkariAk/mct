package mct.gui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import mct.LoggerLevel
import mct.gui.model.LogEntry
import java.util.concurrent.atomic.AtomicLong

private const val BATCH_WINDOW_MILLIS = 40L
private const val MAX_ENTRIES = 5_000
private const val MAX_PENDING_ENTRIES = 4_096
private const val MAX_BATCH_SIZE = 512

private val DEFAULT_LEVELS = setOf(
    LoggerLevel.Info,
    LoggerLevel.Warning,
    LoggerLevel.Error,
    LoggerLevel.Debug,
)

private data class QueuedLog(
    val generation: Long,
    val entry: LogEntry,
)

/**
 * Observable log console fed by a bounded queue.
 *
 * [add] may be called from any thread: entries are queued and only applied to [lines]
 * by [collect], which coalesces bursts into a single observable update and drops
 * entries that a [clear] superseded.
 */
class LogConsoleState {
    val lines = mutableStateListOf(LogEntry(null, "就绪。"))

    /** Levels shown in the console. */
    var levelFilter by mutableStateOf(DEFAULT_LEVELS)

    private val queue = Channel<QueuedLog>(
        capacity = MAX_PENDING_ENTRIES,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val generation = AtomicLong(0)
    private val nextSequence = AtomicLong(1)

    /** Queue [entry], assigning it a stable sequence number when it has none. */
    fun add(entry: LogEntry) {
        val sequenced = if (entry.sequence == 0L) {
            entry.copy(sequence = nextSequence.getAndIncrement())
        } else {
            entry
        }
        queue.trySend(QueuedLog(generation.get(), sequenced))
    }

    /** Drop visible and not-yet-rendered entries, e.g. before a new operation. */
    fun clear() {
        generation.incrementAndGet()
        while (queue.tryReceive().isSuccess) {
            // Drain entries left over by the previous operation.
        }
        lines.clear()
    }

    /** Drain the queue into [lines] until the enclosing coroutine is cancelled. */
    suspend fun collect() {
        val batch = ArrayList<QueuedLog>(MAX_BATCH_SIZE)
        while (currentCoroutineContext().isActive) {
            batch += queue.receive()
            delay(BATCH_WINDOW_MILLIS)
            while (batch.size < MAX_BATCH_SIZE) {
                batch += queue.tryReceive().getOrNull() ?: break
            }

            val current = generation.get()
            val entries = batch.asSequence()
                .filter { it.generation == current }
                .map(QueuedLog::entry)
                .toList()
            batch.clear()
            if (entries.isEmpty()) continue

            val overflow = (lines.size + entries.size - MAX_ENTRIES).coerceAtLeast(0)
            if (overflow > 0) {
                lines.subList(0, minOf(overflow, lines.size)).clear()
            }
            lines.addAll(entries)
        }
    }
}
