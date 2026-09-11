package mct.gui.state

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import arrow.core.raise.either
import com.aallam.openai.client.OpenAI
import kotlinx.coroutines.*
import mct.Env
import mct.LoggerLevel
import mct.extra.ai.ChatCompletionCall
import mct.extra.ai.createOpenAIClient
import mct.extra.ai.translator.TranslateSign
import mct.extra.ai.translator.optimizePrompt
import mct.gui.model.GuiSettings
import mct.gui.model.LogEntry
import mct.gui.model.TranslateState
import mct.gui.services.ClientManager
import mct.gui.services.listModels
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Observable state of the translate panel plus the OpenAI client behind it.
 *
 * The client lifecycle is confined here: [setupApiClient] probes credentials and lists
 * models, [setupChatCompletion] installs a completion call for the selected model, and
 * [close] releases both. Races with UI edits are handled by re-checking credentials
 * before installing a probed client.
 */
class TranslationController(
    val clientManager: ClientManager,
    private val env: Env,
    private val logs: LogConsoleState,
    private val snackbar: SnackbarHostState,
    private val scope: CoroutineScope,
) {
    var state by mutableStateOf(TranslateState())

    // ── Translation progress / token usage ──────────────────────
    var progress by mutableFloatStateOf(0f)
    var status by mutableStateOf("")
    var lastTokenConsume by mutableIntStateOf(0)
    var totalTokenConsume by mutableLongStateOf(0)

    private val disposed = AtomicBoolean(false)

    // Sign callbacks arrive from IO workers, so they hop onto [scope] before
    // touching snapshot state.

    fun onProgress(sign: TranslateSign.Progress) = scope.launch {
        progress = sign.progress
        status = if (sign.progress >= 1f) "完成" else "翻译中..."
    }

    fun onTokenConsume(count: Int) = scope.launch {
        lastTokenConsume = count
        totalTokenConsume += count
    }

    /** Reset the progress display before starting a new translation. */
    fun resetProgress() {
        progress = 0f
        status = ""
    }

    // ── API client ──────────────────────────────────────────────

    /** Probe the configured API URL / token and fetch the available models. */
    suspend fun setupApiClient() {
        val (url, token) = withContext(Dispatchers.Main) {
            state.apiUrl.ifBlank { null } to state.apiToken
        }

        if (token.isBlank()) {
            val previous = withContext(Dispatchers.Main) {
                clientManager.chatCompletionCall = null
                clientManager.openAIClient.also {
                    clientManager.openAIClient = null
                    state = state.copy(availableModels = emptyList(), isModelsLoading = false)
                }
            }
            closeClient(previous)
            return
        }

        withContext(Dispatchers.Main) {
            clientManager.chatCompletionCall = null
            state = state.copy(isModelsLoading = true)
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
                    state.apiUrl.ifBlank { null } == url && state.apiToken == token
                if (disposed.get() || !credentialsStillCurrent) {
                    null
                } else {
                    val old = clientManager.openAIClient
                    clientManager.openAIClient = probedClient
                    state = state.copy(availableModels = models, isModelsLoading = false)
                    installed = true
                    old
                }
            }
            if (!installed) return
            if (previous !== probedClient) closeClient(previous)

            withContext(Dispatchers.Main) {
                if (state.model in models) setupChatCompletion()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                val credentialsStillCurrent =
                    state.apiUrl.ifBlank { null } == url && state.apiToken == token
                if (credentialsStillCurrent) {
                    state = state.copy(availableModels = emptyList(), isModelsLoading = false)
                    logs.add(LogEntry(LoggerLevel.Error, "API 连接失败: ${e.message}"))
                }
            }
        } finally {
            if (!installed) closeClient(candidate)
        }
    }

    /** Create (or switch) the [ChatCompletionCall] for the current model. */
    suspend fun setupChatCompletion() {
        if (clientManager.openAIClient == null) return
        val model = state.model
        if (model.isBlank()) return
        val models = state.availableModels
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
            logs.add(LogEntry(LoggerLevel.Warning, "切换模型失败: ${it.message}"))
        }
    }

    /** Have the LLM improve a literature-style prompt. Logs errors itself. */
    suspend fun optimizePrompt(current: String): String? {
        val cl = clientManager.chatCompletionCall
        if (cl == null) {
            logs.add(LogEntry(LoggerLevel.Error, "请先在 API 设置中连接"))
            return null
        }
        logs.add(LogEntry(null, "正在优化翻译风格提示词..."))
        return either {
            cl.optimizePrompt(current)
        }.onLeft {
            env.logger.error { "优化失败: ${it.message}" }
            scope.launch { snackbar.showSnackbar("优化失败: ${it.message}") }
        }.getOrNull()
    }

    private suspend fun closeClient(client: OpenAI?) {
        if (client == null) return
        withContext(NonCancellable + Dispatchers.IO) {
            runCatching { client.close() }
        }
    }

    /** Release the OpenAI clients and refuse further installs. */
    fun close() {
        disposed.set(true)
        runCatching { clientManager.openAIClient?.close() }
        clientManager.openAIClient = null
        clientManager.chatCompletionCall = null
    }
}
