package mct.gui.state

import androidx.compose.runtime.*
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

/** One find-bar hit: the entry it belongs to, plus the matched range inside that entry's message. */
data class LogHit(
    val sequence: Long,
    val range: IntRange,
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
 *
 * [searchHits] is maintained the same way: it is keyed by entry sequence, so a streaming batch
 * only pays for the entries it appended, and a row resolves its own highlights by binary search.
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

    // ── Find bar ─────────────────────────────────────────────────

    /** Whether the find bar is open. Opened by Ctrl+F / the magnifier button, closed by Escape. */
    var searchVisible by mutableStateOf(false)
        private set

    /** Bumped by [openSearch] so pressing Ctrl+F again re-focuses an already open field. */
    var searchFocusRequest by mutableIntStateOf(0)
        private set

    private var queriedBy by mutableStateOf("")

    /** Case-insensitive console query. Changing it re-indexes [searchHits]. */
    var searchQuery: String
        get() = queriedBy
        set(value) {
            if (queriedBy == value) return
            queriedBy = value
            reindexHits()
        }

    /** Every hit in [visible] order. */
    val searchHits = mutableStateListOf<LogHit>()

    /** Index into [searchHits] that the find bar is focused on; -1 when the query has no hit. */
    var searchHitIndex by mutableIntStateOf(-1)
        private set

    /** The focused hit, or `null` when the query matches nothing. */
    val currentHit: LogHit? get() = searchHits.getOrNull(searchHitIndex)

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
        searchHits.clear()
        searchHitIndex = -1
    }

    /** Open the find bar and focus its field. */
    fun openSearch() {
        searchVisible = true
        searchFocusRequest++
    }

    fun closeSearch() {
        searchVisible = false
    }

    /** Move the focused hit by [delta], wrapping around; no-op without hits. */
    fun stepHit(delta: Int) {
        val size = searchHits.size
        if (size == 0) return
        val current = searchHitIndex.takeIf { it in 0 until size } ?: 0
        searchHitIndex = (current + delta).mod(size)
    }

    /**
     * Matched ranges inside the entry with [sequence].
     *
     * [searchHits] is ordered by sequence, so a composing row finds its own hits with a binary
     * search instead of walking the whole index.
     */
    fun hitsFor(sequence: Long): List<IntRange> {
        if (searchHits.isEmpty()) return emptyList()
        var low = 0
        var high = searchHits.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (searchHits[mid].sequence < sequence) low = mid + 1 else high = mid
        }
        if (low == searchHits.size || searchHits[low].sequence != sequence) return emptyList()
        val ranges = ArrayList<IntRange>(4)
        var index = low
        while (index < searchHits.size && searchHits[index].sequence == sequence) {
            ranges += searchHits[index].range
            index++
        }
        return ranges
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
                val added = entries.filter(::shows)
                // A trim drops hits whose entries left the list, so the index is rebuilt rather
                // than appended to. Trimming is rare: it only happens once the cap is reached.
                if (visible.appendTrimming(added, MAX_ENTRIES)) {
                    reindexHits()
                } else {
                    appendHits(added)
                }
            }
        }
    }

    private fun rebuildVisible() {
        visible.clear()
        visible.addAll(lines.filter(::shows))
        reindexHits()
    }

    /** Index [visible] from scratch; used when the query changes or the filtered list is rebuilt. */
    private fun reindexHits() {
        searchHits.clear()
        appendHits(visible)
        searchHitIndex = if (searchHits.isEmpty()) -1 else 0
    }

    /**
     * Append the hits of [entries]. Appends never shift the existing hits, so the focused index
     * survives a streaming batch; it is only re-seated when it was unset or points past the end.
     */
    private fun appendHits(entries: List<LogEntry>) {
        val query = queriedBy
        if (query.isNotEmpty()) {
            for (entry in entries) {
                for (range in occurrences(entry.message, query)) {
                    searchHits += LogHit(entry.sequence, range)
                }
            }
        }
        searchHitIndex = when {
            searchHits.isEmpty() -> -1
            searchHitIndex !in searchHits.indices -> 0
            else -> searchHitIndex
        }
    }

    private fun shows(entry: LogEntry) = entry.level == null || entry.level in filteredBy
}

/** Append [entries], dropping the oldest when the cap is exceeded; returns whether it dropped. */
private fun MutableList<LogEntry>.appendTrimming(entries: List<LogEntry>, cap: Int): Boolean {
    if (entries.isEmpty()) return false
    val overflow = (size + entries.size - cap).coerceAtLeast(0)
    if (overflow > 0) {
        subList(0, minOf(overflow, size)).clear()
    }
    addAll(entries)
    return overflow > 0
}

/** Non-overlapping, case-insensitive occurrences of [query] in [text], left to right. */
private fun occurrences(text: String, query: String): List<IntRange> {
    val result = ArrayList<IntRange>(1)
    var from = 0
    while (true) {
        val at = text.indexOf(query, from, ignoreCase = true)
        if (at < 0) return result
        result += at until at + query.length
        from = at + query.length
    }
}
