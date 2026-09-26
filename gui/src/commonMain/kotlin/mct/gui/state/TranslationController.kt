@file:OptIn(ExperimentalAtomicApi::class)

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
import mct.gui.platform.ioDispatcher
import mct.gui.services.ClientManager
import mct.gui.services.listModels
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

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

    /** Credentials whose probe succeeded; a repeated setup with the same pair is a no-op. */
    private var lastProbedCredentials: Pair<String, String>? = null

    /** The in-flight "AI optimize" request, if any. */
    private var optimizeJob: Job? = null

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
                lastProbedCredentials = null
                clientManager.openAIClient.also {
                    clientManager.openAIClient = null
                    state = state.copy(availableModels = emptyList(), isModelsLoading = false)
                }
            }
            closeClient(previous)
            return
        }

        // Editing an unrelated field re-runs the caller's effect; skip the network round-trip
        // unless the credentials actually changed.
        if (lastProbedCredentials == (url.orEmpty() to token) && clientManager.openAIClient != null) {
            // The client is still the probed one, but the call may have been cleared by a failed
            // probe in between (the token was edited to a wrong value and back). Without rebuilding
            // it here, "开始翻译" would keep reporting "没有 API 连接" until the app restarts.
            if (clientManager.chatCompletionCall == null) setupChatCompletion()
            return
        }

        withContext(Dispatchers.Main) {
            clientManager.chatCompletionCall = null
            state = state.copy(isModelsLoading = true)
        }

        var candidate: OpenAI? = null
        var installed = false
        try {
            val probedClient = withContext(ioDispatcher) {
                with(env) { createOpenAIClient(url, token) }.also { candidate = it }
            }
            // A missing model list is not a missing connection: several OpenAI-compatible gateways do
            // not implement `GET /models`, and refusing to install the client because of that left the
            // AI engine unusable with no way back. The failure is reported, `availableModels` stays
            // empty — which is also what makes the model field editable — and a wrong credential
            // still surfaces as an error when a translation is actually requested.
            val models = withContext(ioDispatcher) {
                runCatching { probedClient.listModels() }
                    .onFailure { error ->
                        logs.add(
                            LogEntry(
                                LoggerLevel.Warning,
                                "无法获取模型列表（${error.message}），仍按当前配置连接",
                            ),
                        )
                    }
                    .getOrDefault(emptyList())
            }
            currentCoroutineContext().ensureActive()

            val previous = withContext(NonCancellable + Dispatchers.Main) {
                val credentialsStillCurrent =
                    state.apiUrl.ifBlank { null } == url && state.apiToken == token
                if (disposed.load() || !credentialsStillCurrent) {
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
            lastProbedCredentials = url.orEmpty() to token
            if (previous !== probedClient) closeClient(previous)

            withContext(Dispatchers.Main) {
                // Always attempted: `setupChatCompletion` skips a model that is absent from a probed
                // list itself, and with no list at all the configured model is the only candidate.
                setupChatCompletion()
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
            // A probe that did not install its client must not stay remembered as done: the same
            // credentials would then be skipped on the next run and never retried.
            if (!installed) lastProbedCredentials = null
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

    /**
     * Run an "AI optimize" pass over the current literature-style prompt.
     *
     * Runs on [scope] rather than a panel's composition scope: leaving the translate tab must
     * not cancel a paid request, and the result is written into the current state, so edits the
     * user makes while the request is in flight survive.
     */
    fun optimizeLiteratureStyle() {
        if (optimizeJob?.isActive == true) return
        val cl = clientManager.chatCompletionCall
        if (cl == null) {
            logs.add(LogEntry(LoggerLevel.Error, "请先在 API 设置中连接"))
            return
        }
        val current = state.literatureStyle
        state = state.copy(isOptimizing = true)
        optimizeJob = scope.launch {
            try {
                val improved = either {
                    cl.optimizePrompt(current)
                }.onLeft {
                    env.logger.error { "优化失败: ${it.message}" }
                    snackbar.showSnackbar("优化失败: ${it.message}")
                }.getOrNull()
                if (improved != null) state = state.copy(literatureStyle = improved)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                env.logger.error { "优化失败: ${e.message}" }
            } finally {
                state = state.copy(isOptimizing = false)
            }
        }
    }

    private suspend fun closeClient(client: OpenAI?) {
        if (client == null) return
        withContext(NonCancellable + ioDispatcher) {
            runCatching { client.close() }
        }
    }

    /** Release the OpenAI clients and refuse further installs. */
    fun close() {
        disposed.store(true)
        runCatching { clientManager.openAIClient?.close() }
        clientManager.openAIClient = null
        clientManager.chatCompletionCall = null
    }
}
