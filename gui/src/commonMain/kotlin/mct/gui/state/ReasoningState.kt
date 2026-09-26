package mct.gui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import mct.extra.ai.AiSign
import mct.gui.model.GuiSettings
import mct.gui.util.nowMillis

private const val BATCH_WINDOW_MILLIS = 32L
private const val MAX_BATCH_SIZE = 512

/** Publishes at most this often per request while streaming. */
private const val PUBLISH_INTERVAL_MILLIS = 250L

/** ...or as soon as this many characters accumulated since the last publish. */
private const val PUBLISH_CHAR_THRESHOLD = 4_096

/**
 * Streaming reasoning output of in-flight requests, keyed by request id.
 *
 * [accept] may be called from any thread; [collect] applies queued updates in batches.
 *
 * Accumulation happens in a [StringBuilder] per request and only reaches observable
 * state in [contents] while the sheet is [visible]: streaming would otherwise copy the
 * whole text and write snapshot state on every batch, and re-laying out the growing
 * text is the expensive part. Opening the sheet publishes what accumulated meanwhile.
 */
class ReasoningState {
    /** Published text per request id; only maintained while [visible]. */
    val contents = mutableStateMapOf<Int, String>()

    /** Whether each request is still streaming; only maintained while [visible]. */
    val active = mutableStateMapOf<Int, Boolean>()

    private val builders = LinkedHashMap<Int, StringBuilder>()
    private val terminated = LinkedHashMap<Int, Boolean>()
    private val publishedLength = HashMap<Int, Int>()
    private val publishedAt = HashMap<Int, Long>()

    private var opened by mutableStateOf(false)

    /** Whether the reasoning sheet is open. Opening publishes accumulated text. */
    var visible: Boolean
        get() = opened
        set(value) {
            if (opened == value) return
            opened = value
            if (value) publishAll() else clearPublished()
        }

    private val queue = Channel<AiSign.Reasoning>(capacity = Channel.UNLIMITED)

    fun accept(update: AiSign.Reasoning) {
        queue.trySend(update)
    }

    /** Forget all buffered reasoning, e.g. when the user clears the sheet. */
    fun clear() {
        builders.clear()
        terminated.clear()
        publishedLength.clear()
        publishedAt.clear()
        clearPublished()
    }

    /** Drain the queue into [contents] / [active] until cancelled. */
    suspend fun collect() {
        val batch = ArrayList<AiSign.Reasoning>(MAX_BATCH_SIZE)
        while (currentCoroutineContext().isActive) {
            batch += queue.receive()
            delay(BATCH_WINDOW_MILLIS)
            while (batch.size < MAX_BATCH_SIZE) {
                batch += queue.tryReceive().getOrNull() ?: break
            }

            apply(batch)
            batch.clear()
        }
    }

    /**
     * Streaming responses deliver deltas, so they are appended; one-shot responses
     * deliver the whole text every time, so they replace.
     */
    private fun apply(batch: List<AiSign.Reasoning>) {
        val touched = LinkedHashSet<Int>()
        batch.forEach { update ->
            val builder = builders.getOrPut(update.id, ::StringBuilder)
            if (GuiSettings.useStreamApi) {
                builder.append(update.reasoningContent)
            } else {
                builder.setLength(0)
                builder.append(update.reasoningContent)
            }
            terminated[update.id] = update.terminated
            touched += update.id
        }

        if (!opened) return
        touched.forEach(::publish)
    }

    private fun publish(id: Int, force: Boolean = false) {
        // A finished request must be published immediately; while streaming, throttle so the
        // growing text is not copied into snapshot state on every 32 ms batch.
        val finished = terminated[id] == true
        val length = builders[id]?.length ?: 0
        if (!force) {
            if (!finished && !opened) return
            if (!finished) {
                val grewBy = length - (publishedLength[id] ?: 0)
                val elapsed = nowMillis() - (publishedAt[id] ?: 0L)
                if (grewBy < PUBLISH_CHAR_THRESHOLD && elapsed < PUBLISH_INTERVAL_MILLIS) return
            }
        }
        contents[id] = builders[id]?.toString().orEmpty()
        active[id] = !finished
        publishedLength[id] = length
        publishedAt[id] = nowMillis()
    }

    private fun publishAll() {
        builders.keys.forEach { publish(it, force = true) }
    }

    private fun clearPublished() {
        contents.clear()
        active.clear()
        publishedLength.clear()
        publishedAt.clear()
    }
}
