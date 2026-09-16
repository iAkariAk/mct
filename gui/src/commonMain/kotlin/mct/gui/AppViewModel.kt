package mct.gui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*
import mct.Env
import mct.Notifier
import mct.cli.NotifierHooks
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

    /** Set by [dispose]; late sign callbacks from in-process CLI runs are dropped. */
    private var disposed = false

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
    val project = ProjectController(env, scope, operations, snackbarHostState)

    // ── Panel data states ───────────────────────────────────────
    var selectedTab by mutableStateOf(Tab.Extract)
    var extractState by mutableStateOf(ExtractState())
    var termExtractState by mutableStateOf(TermExtractState())
    var backfillState by mutableStateOf(BackfillState())
    var patchState by mutableStateOf(PatchState())
    var toolboxState by mutableStateOf(ToolboxState())

    init {
        // In-process CLI runs (the project workflow) publish through NotifierHooks, which is
        // separate from this VM's own Notifier; subscribe so their progress/token/reasoning
        // signs reach the GUI.
        NotifierHooks.onTranslateSign(::onTranslateSign)
        NotifierHooks.onAiSign(::onAiSign)
        scope.launch(Dispatchers.Default) { logs.collect() }
        scope.launch { reasoning.collect() }
    }

    /**
     * Sign callbacks arrive from IO workers; the holders hop back onto the
     * UI dispatcher themselves.
     */
    private fun onTranslateSign(sign: TranslateSign) {
        if (disposed) return
        when (sign) {
            is TranslateSign.Progress -> translation.onProgress(sign)
        }
    }

    private fun onAiSign(sign: AiSign) {
        if (disposed) return
        when (sign) {
            is AiSign.ConsumeToken -> translation.onTokenConsume(sign.count)
            is AiSign.Reasoning -> reasoning.accept(sign)
        }
    }

    /** Must be called by the owning composable's `DisposableEffect` cleanup. */
    fun dispose() {
        disposed = true
        translation.close()
        scope.cancel()
    }
}
