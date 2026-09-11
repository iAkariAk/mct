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

private const val BATCH_WINDOW_MILLIS = 32L
private const val MAX_BATCH_SIZE = 512

/**
 * Streaming reasoning output of in-flight requests, keyed by request id.
 *
 * [accept] may be called from any thread; [collect] applies queued updates in batches
 * so the sheet re-renders at most once per batch window.
 */
class ReasoningState {
    val contents = mutableStateMapOf<Int, String>()
    val active = mutableStateMapOf<Int, Boolean>()

    /** Whether the reasoning sheet is open. */
    var visible by mutableStateOf(false)

    private val queue = Channel<AiSign.Reasoning>(capacity = Channel.UNLIMITED)

    fun accept(update: AiSign.Reasoning) {
        queue.trySend(update)
    }

    /** Forget all buffered reasoning, e.g. when the user clears the sheet. */
    fun clear() {
        contents.clear()
        active.clear()
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
        if (GuiSettings.useStreamApi) {
            val chunksById = linkedMapOf<Int, StringBuilder>()
            batch.forEach { update ->
                chunksById.getOrPut(update.id, ::StringBuilder)
                    .append(update.reasoningContent)
                active[update.id] = !update.terminated
            }
            chunksById.forEach { (id, chunks) ->
                contents[id] = contents[id].orEmpty() + chunks
            }
        } else {
            batch.forEach { update ->
                contents[update.id] = update.reasoningContent
                active[update.id] = !update.terminated
            }
        }
    }
}
