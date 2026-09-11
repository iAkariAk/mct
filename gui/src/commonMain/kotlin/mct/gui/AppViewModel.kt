package mct.gui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import arrow.core.raise.either
import com.aallam.openai.client.OpenAI
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import mct.Env
import mct.LoggerLevel
import mct.Notifier
import mct.extra.ai.AiSign
import mct.extra.ai.ChatCompletionCall
import mct.extra.ai.createOpenAIClient
import mct.extra.ai.translator.TranslateSign
import mct.extra.ai.translator.optimizePrompt
import mct.gui.model.*
import mct.gui.services.*
import mct.on
import okio.FileSystem
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.seconds

private const val LOG_BATCH_WINDOW_MILLIS = 40L
private const val REASONING_BATCH_WINDOW_MILLIS = 32L
private const val MAX_LOG_ENTRIES = 5_000
private const val MAX_PENDING_LOG_ENTRIES = 4_096
private const val MAX_BATCH_SIZE = 512

/** 设置自动保存的防抖窗口：停止编辑该时长后写入。 */
private val AUTO_SAVE_DEBOUNCE = 3.seconds

private data class QueuedLog(
    val generation: Long,
    val entry: LogEntry,
)

/**
 * Centralized ViewModel for the entire application UI.
 *
 * Owns all Compose-observable state and exposes helper methods
 * that the [App] composable and its children call in response to user actions.
 */
class AppViewModel(
    val clientManager: ClientManager,
) {
    /**
     * Internal scope tied to this ViewModel's lifetime.  Cancelled
     * when the composable that created us leaves the composition.
     */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Must be called by the owning composable's `DisposableEffect` cleanup. */
    fun dispose() {
        disposed.set(true)
        logQueue.close()
        reasoningQueue.close()
        scope.cancel()
        clientManager.chatCompletionCall = null
        runCatching { clientManager.openAIClient?.close() }
        clientManager.openAIClient = null
    }

    // ── Tab ────────────────────────────────────────────────────
    var selectedTab by mutableStateOf(Tab.Extract)

    // ── Operation state ─────────────────────────────────────────
    var isRunning by mutableStateOf(false)
    private var _currentJob: Job? = null  // not observable – only cancelJob / launchOp touch it

    // ── Panel data states ───────────────────────────────────────
    var extractState by mutableStateOf(ExtractState())
    var translateState by mutableStateOf(TranslateState())
    var termExtractState by mutableStateOf(TermExtractState())
    var backfillState by mutableStateOf(BackfillState())
    var patchState by mutableStateOf(PatchState())
    var projectState by mutableStateOf(ProjectWorkflowState())
    var toolboxState by mutableStateOf(ToolboxState())

    // ── Translation progress ────────────────────────────────────
    var translateProgress by mutableFloatStateOf(0f)
    var translateStatus by mutableStateOf("")

    // ── Token consumption display ───────────────────────────────
    var lastTokenConsume by mutableIntStateOf(0)
    var totalTokenConsume by mutableLongStateOf(0)

    // ── Reasoning sheet ─────────────────────────────────────────
    val reasoningContents = mutableStateMapOf<Int, String>()
    val reasoningActive = mutableStateMapOf<Int, Boolean>()
    var showReasoning by mutableStateOf(false)

    // ── Log console ─────────────────────────────────────────────
    val logLines = mutableStateListOf(LogEntry(null, "就绪。"))
    var logLevelFilter by mutableStateOf(
        setOf(LoggerLevel.Info, LoggerLevel.Warning, LoggerLevel.Error, LoggerLevel.Debug)
    )
    private val logQueue = Channel<QueuedLog>(
        capacity = MAX_PENDING_LOG_ENTRIES,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val reasoningQueue = Channel<AiSign.Reasoning>(capacity = Channel.UNLIMITED)
    private val nextLogSequence = AtomicLong(1L)
    private val logGeneration = AtomicLong(0L)
    private val disposed = AtomicBoolean(false)

    // ── Snackbar ────────────────────────────────────────────────
    val snackbarHostState = SnackbarHostState()

    /** Last snapshot successfully written to disk; guards no-op auto-saves. */
    private var lastSavedSettings: ApiSettings? = null

    // ── Infrastructure ──────────────────────────────────────────

    val guiLogger = GuiLogger(::addLog)
    val notifier = Notifier {
        on<TranslateSign> { sign ->
            when (sign) {
                is TranslateSign.Progress -> {
                    scope.launch {
                        translateProgress = sign.progress
                        translateStatus = if (sign.progress >= 1f) "完成" else "翻译中..."
                    }
                }
            }
        }
        on<AiSign> { sign ->
            when (sign) {
                is AiSign.ConsumeToken -> {
                    scope.launch {
                        lastTokenConsume = sign.count
                        totalTokenConsume += sign.count
                    }
                }

                is AiSign.Reasoning -> reasoningQueue.trySend(sign)
            }
        }
    }
    val env = Env(fs = FileSystem.SYSTEM, logger = guiLogger, notifier = notifier)

    init {
        scope.launch { collectLogs() }
        scope.launch { collectReasoningUpdates() }
    }

    /**
     * Logger callbacks can arrive from IO workers. Queue them so snapshot state is only
     * mutated on the UI dispatcher, and coalesce bursts into one structural list update.
     */
    fun addLog(entry: LogEntry) {
        val sequenced = if (entry.sequence == 0L) {
            entry.copy(sequence = nextLogSequence.getAndIncrement())
        } else {
            entry
        }
        logQueue.trySend(QueuedLog(logGeneration.get(), sequenced))
    }

    /** Clear visible and not-yet-rendered logs before starting a new operation. */
    fun clearLogs() {
        logGeneration.incrementAndGet()
        while (logQueue.tryReceive().isSuccess) {
            // Drain queued entries from the previous operation.
        }
        logLines.clear()
    }

    private suspend fun collectLogs() {
        val batch = ArrayList<QueuedLog>(MAX_BATCH_SIZE)
        while (currentCoroutineContext().isActive) {
            batch += logQueue.receive()
            delay(LOG_BATCH_WINDOW_MILLIS)
            while (batch.size < MAX_BATCH_SIZE) {
                batch += logQueue.tryReceive().getOrNull() ?: break
            }

            val currentGeneration = logGeneration.get()
            val entries = batch.asSequence()
                .filter { it.generation == currentGeneration }
                .map(QueuedLog::entry)
                .toList()
            if (entries.isNotEmpty()) {
                val overflow = (logLines.size + entries.size - MAX_LOG_ENTRIES).coerceAtLeast(0)
                if (overflow > 0) {
                    logLines.subList(0, minOf(overflow, logLines.size)).clear()
                }
                logLines.addAll(entries)
            }
            batch.clear()
        }
    }

    /** Limit streaming reasoning updates to roughly one UI update per frame. */
    private suspend fun collectReasoningUpdates() {
        val batch = ArrayList<AiSign.Reasoning>(MAX_BATCH_SIZE)
        while (currentCoroutineContext().isActive) {
            batch += reasoningQueue.receive()
            delay(REASONING_BATCH_WINDOW_MILLIS)
            while (batch.size < MAX_BATCH_SIZE) {
                batch += reasoningQueue.tryReceive().getOrNull() ?: break
            }

            if (GuiSettings.useStreamApi) {
                val chunksById = linkedMapOf<Int, StringBuilder>()
                batch.forEach { update ->
                    chunksById.getOrPut(update.id, ::StringBuilder)
                        .append(update.reasoningContent)
                    reasoningActive[update.id] = !update.terminated
                }
                chunksById.forEach { (id, chunks) ->
                    reasoningContents[id] = reasoningContents[id].orEmpty() + chunks
                }
            } else {
                batch.forEach { update ->
                    reasoningContents[update.id] = update.reasoningContent
                    reasoningActive[update.id] = !update.terminated
                }
            }
            batch.clear()
        }
    }

    // ── Operations ──────────────────────────────────────────────

    /**
     * Launch a long-running operation in [scope], managing [isRunning] / job
     * lifecycle and routing exceptions to logs / snackbar.
     */
    fun launchOp(prelude: () -> Unit, block: suspend CoroutineScope.() -> Unit) {
        _currentJob?.cancel()
        prelude()
        _currentJob = scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                addLog(LogEntry(null, "操作已被用户取消"))
                throw e
            } catch (e: Exception) {
                addLog(LogEntry(LoggerLevel.Error, e.stackTraceToString()))
                scope.launch { snackbarHostState.showSnackbar(e.message ?: "未知错误") }
            } finally {
                isRunning = false
                _currentJob = null
            }
        }
    }

    /** Cancel the currently running job. */
    fun cancelJob() {
        _currentJob?.cancel()
    }

    // ── API Settings ────────────────────────────────────────────

    /**
     * Snapshot of every persisted field, readable from a `snapshotFlow`.
     *
     * The same value drives debounced auto-saving, so adding a settings field only
     * requires extending this method (plus [ApiSettings]).
     */
    fun persistedSettings() = ApiSettings(
        apiUrl = translateState.apiUrl,
        model = translateState.model,
        apiToken = translateState.apiToken,
        useStreamApi = GuiSettings.useStreamApi,
        tokenThreshold = GuiSettings.tokenThreshold,
        temperature = GuiSettings.temperature,
        concurrency = GuiSettings.concurrency,
        concurrentByKind = GuiSettings.concurrentByKind,
        engine = translateState.engine,
        api = translateState.api,
    )

    /**
     * 自动保存：[persistedSettings] 停止变化 [AUTO_SAVE_DEBOUNCE] 后写入磁盘。
     *
     * 由 UI 层在组合中启动；跳过首个快照以避免启动时无谓写盘。
     */
    @OptIn(FlowPreview::class)
    suspend fun autoSaveSettings() {
        snapshotFlow { persistedSettings() }
            .drop(1)
            .distinctUntilChanged()
            .debounce(AUTO_SAVE_DEBOUNCE)
            .collect { settings ->
                if (!saveSettings(settings)) {
                    addLog(LogEntry(LoggerLevel.Warning, "自动保存设置失败: ${apiSetting.path}"))
                }
            }
    }

    /** Read persisted settings into UI state. */
    suspend fun loadSettings() = withContext(Dispatchers.IO) {
        val saved = apiSetting.load()
        val theme = themeSetting.load()
        withContext(Dispatchers.Main) {
            translateState = translateState.copy(
                apiUrl = saved.apiUrl,
                model = saved.model,
                apiToken = saved.apiToken,
                engine = saved.engine,
                api = saved.api,
            )
            GuiSettings.temperature = saved.temperature
            GuiSettings.useStreamApi = saved.useStreamApi
            GuiSettings.tokenThreshold = saved.tokenThreshold
            GuiSettings.concurrency = saved.concurrency
            GuiSettings.concurrentByKind = saved.concurrentByKind
            GuiSettings.seedColorArgb = theme.seedColorArgb
            if (theme.seedColorArgb != 0) GuiSettings.isDynamicThemeEnabled = true
            lastSavedSettings = persistedSettings()
            if (saved.apiUrl.isNotBlank() || saved.apiToken.isNotBlank())
                addLog(LogEntry(null, "已加载 API 设置 (${apiSetting.path})"))
        }
    }

    /** Persist [settings] unless it is identical to the last written snapshot. */
    suspend fun saveSettings(settings: ApiSettings): Boolean {
        if (settings == lastSavedSettings) return true
        val saved = withContext(Dispatchers.IO) { apiSetting.save(settings) }
        if (saved) lastSavedSettings = settings
        return saved
    }

    /** Probe the configured API URL / token and fetch available models. */
    suspend fun setupApiClient() {
        val (url, token) = withContext(Dispatchers.Main) {
            translateState.apiUrl.ifBlank { null } to translateState.apiToken
        }

        if (token.isBlank()) {
            val previous = withContext(Dispatchers.Main) {
                clientManager.chatCompletionCall = null
                clientManager.openAIClient.also {
                    clientManager.openAIClient = null
                    translateState = translateState.copy(
                        availableModels = emptyList(),
                        isModelsLoading = false,
                    )
                }
            }
            closeClient(previous)
            return
        }

        withContext(Dispatchers.Main) {
            clientManager.chatCompletionCall = null
            translateState = translateState.copy(isModelsLoading = true)
        }

        var candidate: OpenAI? = null
        var installed = false
        try {
            val probedClient = withContext(Dispatchers.IO) {
                with(env) { createOpenAIClient(url, token) }.also { candidate = it }
            }
            val models = withContext(Dispatchers.IO) { probedClient.listModels() }
            currentCoroutineContext().ensureActive()

            val previous = withContext(NonCancellable + Dispatchers.Main) {
                val credentialsStillCurrent =
                    translateState.apiUrl.ifBlank { null } == url && translateState.apiToken == token
                if (disposed.get() || !credentialsStillCurrent) {
                    null
                } else {
                    val old = clientManager.openAIClient
                    clientManager.openAIClient = probedClient
                    translateState = translateState.copy(
                        availableModels = models,
                        isModelsLoading = false,
                    )
                    installed = true
                    old
                }
            }
            if (!installed) return
            if (previous !== probedClient) closeClient(previous)

            withContext(Dispatchers.Main) {
                if (translateState.model in models) setupChatCompletion()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                val credentialsStillCurrent =
                    translateState.apiUrl.ifBlank { null } == url && translateState.apiToken == token
                if (credentialsStillCurrent) {
                    translateState = translateState.copy(
                        availableModels = emptyList(),
                        isModelsLoading = false,
                    )
                    addLog(LogEntry(LoggerLevel.Error, "API 连接失败: ${e.message}"))
                }
            }
        } finally {
            if (!installed) closeClient(candidate)
        }
    }

    private suspend fun closeClient(client: OpenAI?) {
        if (client == null) return
        withContext(NonCancellable + Dispatchers.IO) {
            runCatching { client.close() }
        }
    }

    /** Create (or switch) a [ChatCompletionCall] for the current model. */
    suspend fun setupChatCompletion() {
        if (clientManager.openAIClient == null) return
        val model = translateState.model
        if (model.isBlank()) return
        val models = translateState.availableModels
        if (models.isNotEmpty() && model !in models) return

        with(env) {
            either {
                clientManager.chatCompletionCall = ChatCompletionCall(
                    client = clientManager.openAIClient!!,
                    model = model,
                    useStreamApi = GuiSettings.useStreamApi,
                    strict = false,
                    temperature = GuiSettings.temperature,
                )
            }
        }.onLeft {
            addLog(LogEntry(LoggerLevel.Warning, "切换模型失败: ${it.message}"))
        }
    }

    /** Have the LLM improve a literature-style prompt. Logs errors itself. */
    suspend fun optimizePrompt(current: String): String? {
        val cl = clientManager.chatCompletionCall
        if (cl == null) {
            addLog(LogEntry(LoggerLevel.Error, "请先在 API 设置中连接"))
            return null
        }
        addLog(LogEntry(null, "正在优化翻译风格提示词..."))
        return either {
            cl.optimizePrompt(current)
        }.onLeft {
            env.logger.error { "优化失败: ${it.message}" }
            scope.launch { snackbarHostState.showSnackbar("优化失败: ${it.message}") }
        }.getOrNull()
    }
}
