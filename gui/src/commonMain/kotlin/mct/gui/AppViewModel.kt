package mct.gui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*
import mct.Env
import mct.Notifier
import mct.extra.ai.AiSign
import mct.extra.ai.translator.TranslateSign
import mct.gui.model.*
import mct.gui.services.ClientManager
import mct.gui.services.GuiLogger
import mct.gui.state.*
import mct.on
import okio.FileSystem

/**
 * Application state container.
 *
 * This is only the composition root: it wires the state holders in [mct.gui.state]
 * together, owns the coroutine scope they run in, and holds the panel states.
 * Behaviour lives in the holders, not here.
 */
class AppViewModel(clientManager: ClientManager) {
    /** Scope tied to this ViewModel's lifetime; cancelled by [dispose]. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val snackbarHostState = SnackbarHostState()

    val logs = LogConsoleState()
    val reasoning = ReasoningState()

    private val notifier = Notifier {
        on<TranslateSign> { onTranslateSign(it) }
        on<AiSign> { onAiSign(it) }
    }
    val env = Env(fs = FileSystem.SYSTEM, logger = GuiLogger(logs::add), notifier = notifier)

    val translation = TranslationController(clientManager, env, logs, snackbarHostState, scope)
    val operations = OperationRunner(scope, logs, snackbarHostState)
    val settings = SettingsController(logs, translation)

    // ── Panel data states ───────────────────────────────────────
    var selectedTab by mutableStateOf(Tab.Extract)
    var extractState by mutableStateOf(ExtractState())
    var termExtractState by mutableStateOf(TermExtractState())
    var backfillState by mutableStateOf(BackfillState())
    var patchState by mutableStateOf(PatchState())
    var projectState by mutableStateOf(ProjectWorkflowState())
    var toolboxState by mutableStateOf(ToolboxState())

    init {
        scope.launch { logs.collect() }
        scope.launch { reasoning.collect() }
    }

    /**
     * Sign callbacks arrive from IO workers; the holders hop back onto the
     * UI dispatcher themselves.
     */
    private fun onTranslateSign(sign: TranslateSign) {
        when (sign) {
            is TranslateSign.Progress -> translation.onProgress(sign)
        }
    }

    private fun onAiSign(sign: AiSign) {
        when (sign) {
            is AiSign.ConsumeToken -> translation.onTokenConsume(sign.count)
            is AiSign.Reasoning -> reasoning.accept(sign)
        }
    }

    /** Must be called by the owning composable's `DisposableEffect` cleanup. */
    fun dispose() {
        translation.close()
        scope.cancel()
    }
}
