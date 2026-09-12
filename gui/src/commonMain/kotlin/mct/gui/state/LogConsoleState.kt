package mct.gui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
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
 * [add] may be called from any thread: entries are queued and only applied to the
 * visible state by [collect], which coalesces bursts into a single update and drops
 * entries that a [clear] superseded.
 *
 * [visible] is maintained incrementally rather than re-filtered from [lines] on every
 * batch. Appends are the common case, and re-filtering would hand the log list a fresh
 * instance each batch, forcing the lazy list downstream to re-diff every key.
 * Only a [levelFilter] change rebuilds it.
 */
class LogConsoleState {
    /** Every entry received, including those hidden by [levelFilter]. */
    val lines = mutableStateListOf(LogEntry(null, "就绪。"))

    /** Entries matching [levelFilter], in arrival order. */
    val visible = mutableStateListOf<LogEntry>()

    private var filteredBy by mutableStateOf(DEFAULT_LEVELS)

    /** Levels shown in the console. Changing it rebuilds [visible]. */
    var levelFilter: Set<LoggerLevel>
        get() = filteredBy
        set(value) {
            if (filteredBy == value) return
            filteredBy = value
            rebuildVisible()
        }

    private val queue = Channel<QueuedLog>(
        capacity = MAX_PENDING_ENTRIES,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val generation = AtomicLong(0)
    private val nextSequence = AtomicLong(1)

    init {
        rebuildVisible()
    }

    /** Queue [entry], assigning it a stable sequence number when it has none. */
    fun add(entry: LogEntry) {
        val sequenced = if (entry.sequence == 0L) {
            entry.copy(sequence = nextSequence.getAndIncrement())
        } else {
            entry
        }
        queue.trySend(QueuedLog(generation.get(), sequenced))
    }

    /** Drop visible and queued entries, e.g. before a new operation. */
    fun clear() {
        generation.incrementAndGet()
        while (queue.tryReceive().isSuccess) {
            // Drain entries left over by the previous operation.
        }
        lines.clear()
        visible.clear()
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

            // Filtering and materialising the batch is pure CPU work; only the observable
            // list mutations hop onto the UI thread.
            val current = generation.get()
            val entries = batch.asSequence()
                .filter { it.generation == current }
                .map(QueuedLog::entry)
                .toList()
            batch.clear()
            if (entries.isEmpty()) continue

            withContext(Dispatchers.Main.immediate) {
                lines.appendTrimming(entries, MAX_ENTRIES)
                visible.appendTrimming(entries.filter(::shows), MAX_ENTRIES)
            }
        }
    }

    private fun rebuildVisible() {
        visible.clear()
        visible.addAll(lines.filter(::shows))
    }

    private fun shows(entry: LogEntry) = entry.level == null || entry.level in filteredBy
}

/** Append [entries], dropping the oldest when the cap is exceeded. */
private fun MutableList<LogEntry>.appendTrimming(entries: List<LogEntry>, cap: Int) {
    if (entries.isEmpty()) return
    val overflow = (size + entries.size - cap).coerceAtLeast(0)
    if (overflow > 0) {
        subList(0, minOf(overflow, size)).clear()
    }
    addAll(entries)
}
