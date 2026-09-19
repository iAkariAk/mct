package mct.gui.state

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import mct.LoggerLevel
import mct.gui.model.LogEntry

/**
 * Runs one background operation at a time.
 *
 * [launch] clears the console, flips [isRunning], and routes failures to the log console
 * and the snackbar. Launching while another operation runs cancels the previous one;
 * a cancelled operation cannot clear [isRunning] for its successor because stale
 * completions are ignored.
 */
class OperationRunner(
    private val scope: CoroutineScope,
    private val logs: LogConsoleState,
    private val snackbar: SnackbarHostState,
) {
    var isRunning by mutableStateOf(false)
        private set

    /**
     * Whether the running operation is an AI/API translation.
     *
     * The translate panel's progress readout and its cancel affordance belong to a translation, and
     * [isRunning] alone is shared by every panel: without this, translating to completion and then
     * running anything else left the panel showing "翻译进度 100% · 完成" for an unrelated run.
     */
    var isTranslating by mutableStateOf(false)
        private set

    private var job: Job? = null
    private var generation = 0L

    fun launch(isTranslation: Boolean = false, block: suspend CoroutineScope.() -> Unit) {
        val token = ++generation
        job?.cancel()
        logs.clear()
        isRunning = true
        isTranslating = isTranslation
        job = scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                logs.add(LogEntry(null, "操作已被用户取消"))
                throw e
            } catch (e: Exception) {
                logs.add(LogEntry(LoggerLevel.Error, e.stackTraceToString()))
                scope.launch { snackbar.showSnackbar(e.message ?: "未知错误") }
            } finally {
                if (generation == token) {
                    isRunning = false
                    isTranslating = false
                    job = null
                }
            }
        }
    }

    /** Cancel the running operation, if any. */
    fun cancel() {
        job?.cancel()
    }
}
