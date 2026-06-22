package mct.gui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import arrow.core.raise.either
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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

/**
 * Centralized ViewModel for the entire application UI.
 *
 * Owns all Compose-observable state and exposes helper methods
 * that the [App] composable and its children call in response to user actions.
 */
class AppViewModel(
    val scope: CoroutineScope,
    val clientManager: ClientManager,
) {
    // ── Tab ────────────────────────────────────────────────────
    var selectedTab by mutableStateOf(Tab.Extract)

    // ── Operation state ─────────────────────────────────────────
    var isRunning by mutableStateOf(false)
    var currentJob by mutableStateOf<Job?>(null)

    // ── Panel data states ───────────────────────────────────────
    var extractState by mutableStateOf(ExtractState())
    var translateState by mutableStateOf(TranslateState())
    var backfillState by mutableStateOf(BackfillState())
    var toolboxState by mutableStateOf(ToolboxState())

    // ── Translation progress ────────────────────────────────────
    var translateProgress by mutableFloatStateOf(0f)
    var translateStatus by mutableStateOf("")

    // ── Token consumption display ───────────────────────────────
    var lastTokenConsume by mutableIntStateOf(0)
    var totalTokenConsume by mutableIntStateOf(0)

    // ── Reasoning sheet ─────────────────────────────────────────
    val reasoningContents = mutableStateMapOf<Int, StringBuilder>()
    var reasoningContentVersion by mutableIntStateOf(0)
    var showReasoning by mutableStateOf(false)

    // ── Log console ─────────────────────────────────────────────
    val logLines = mutableStateListOf(LogEntry(null, "就绪。"))
    var logLevelFilter by mutableStateOf(
        setOf(LoggerLevel.Info, LoggerLevel.Warning, LoggerLevel.Error, LoggerLevel.Debug)
    )

    // ── Snackbar ────────────────────────────────────────────────
    val snackbarHostState = SnackbarHostState()

    // ── Infrastructure ──────────────────────────────────────────

    val guiLogger = GuiLogger { entry -> logLines.add(entry) }
    val notifier = Notifier {
        on<TranslateSign> { sign ->
            when (sign) {
                is TranslateSign.Progress -> {
                    translateProgress = sign.progress
                    translateStatus = if (sign.progress >= 1f) "完成" else "翻译中..."
                }
            }
        }
        on<AiSign> { sign ->
            when (sign) {
                is AiSign.ConsumeToken -> {
                    lastTokenConsume = sign.count
                    totalTokenConsume += sign.count
                }

                is AiSign.Reasoning -> {
                    val reasoningContent = reasoningContents.getOrPut(sign.id, ::StringBuilder)
                    if (!GuiSettings.useStreamApi) reasoningContent.clear()
                    reasoningContent.append(sign.reasoningContent)
                    reasoningContentVersion++
                }
            }
        }
    }
    val env = Env(fs = FileSystem.SYSTEM, logger = guiLogger, notifier = notifier)

    // ── Operations ──────────────────────────────────────────────

    /**
     * Launch a long-running operation in [scope], managing [isRunning] / [currentJob]
     * lifecycle and routing exceptions to logs / snackbar.
     */
    fun launchOp(prelude: () -> Unit, block: suspend CoroutineScope.() -> Unit) {
        prelude()
        currentJob = scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                logLines.add(LogEntry(null, "操作已被用户取消"))
                throw e
            } catch (e: Exception) {
                logLines.add(LogEntry(LoggerLevel.Error, e.stackTraceToString()))
                scope.launch { snackbarHostState.showSnackbar(e.message ?: "未知错误") }
            } finally {
                isRunning = false
                currentJob = null
            }
        }
    }

    /** Cancel the currently running job. */
    fun cancelJob() {
        currentJob?.cancel()
    }

    // ── API Settings ────────────────────────────────────────────

    /** Read persisted settings into UI state. */
    suspend fun loadSettings() {
        val saved = apiSetting.load()
        translateState = translateState.copy(
            apiUrl = saved.apiUrl,
            model = saved.model,
            apiToken = saved.apiToken,
        )
        GuiSettings.temperature = saved.temperature
        GuiSettings.useStreamApi = saved.useStreamApi
        GuiSettings.tokenThreshold = saved.tokenThreshold
        GuiSettings.concurrency = saved.concurrency
        GuiSettings.concurrentByKind = saved.concurrentByKind
        if (saved.apiUrl.isNotBlank() || saved.apiToken.isNotBlank())
            logLines.add(LogEntry(null, "已加载 API 设置 (${apiSetting.path})"))
    }

    /** Persist current settings. */
    fun saveSettings(): Boolean = apiSetting.save(
        ApiSettings(
            apiUrl = translateState.apiUrl,
            model = translateState.model,
            apiToken = translateState.apiToken,
            useStreamApi = GuiSettings.useStreamApi,
            tokenThreshold = GuiSettings.tokenThreshold,
            temperature = GuiSettings.temperature,
            concurrency = GuiSettings.concurrency,
            concurrentByKind = GuiSettings.concurrentByKind,
        )
    )

    /** Probe the configured API URL / token and fetch available models. */
    suspend fun setupApiClient() {
        clientManager.chatCompletionCall = null
        val url = translateState.apiUrl.ifBlank { null }
        val token = translateState.apiToken
        if (url == null || token.isBlank()) return

        translateState = translateState.copy(isModelsLoading = true)
        runCatching {
            with(env) {
                clientManager.openAIClient = createOpenAIClient(url, token)
                clientManager.openAIClient!!.listModels()
            }
        }.onSuccess { models ->
            translateState = translateState.copy(availableModels = models, isModelsLoading = false)
            if (translateState.model in models) setupChatCompletion()
        }.onFailure { e ->
            translateState = translateState.copy(availableModels = emptyList(), isModelsLoading = false)
            logLines.add(LogEntry(LoggerLevel.Error, "API 连接失败: ${e.message}"))
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
            logLines.add(LogEntry(LoggerLevel.Warning, "切换模型失败: ${it.message}"))
        }
    }

    /** Have the LLM improve a literature-style prompt. Logs errors itself. */
    suspend fun optimizePrompt(current: String): String? {
        val cl = clientManager.chatCompletionCall
        if (cl == null) {
            logLines.add(LogEntry(LoggerLevel.Error, "请先在 API 设置中连接"))
            return null
        }
        logLines.add(LogEntry(null, "正在优化翻译风格提示词..."))
        return either {
            cl.optimizePrompt(current)
        }.onLeft {
            env.logger.error { "优化失败: ${it.message}" }
            scope.launch { snackbarHostState.showSnackbar("优化失败: ${it.message}") }
        }.getOrNull()
    }
}
