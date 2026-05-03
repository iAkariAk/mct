package mct.extra.ai

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.raise
import com.aallam.openai.api.chat.*
import com.aallam.openai.api.core.Usage
import com.aallam.openai.api.exception.GenericIOException
import com.aallam.openai.api.exception.OpenAIHttpException
import com.aallam.openai.api.exception.OpenAITimeoutException
import com.aallam.openai.api.logging.LogLevel
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.LoggingConfig
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIHost
import io.ktor.client.plugins.logging.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import mct.Env
import mct.EnvHolder
import mct.MCTError

private const val MAX_RETRY = 20

sealed interface ChatCompletionCallError : MCTError {
    data class ModelNotFound(val mode: String) : ChatCompletionCallError {
        override val message = "Model $mode not found"
    }

    data class UnvalidatedApi(override val message: String) : ChatCompletionCallError

    data class RetryTooMuch(val maxRetry: Int) : ChatCompletionCallError {
        override val message = "Retry too much, exceeding max retry $maxRetry"
    }
}

context(env: Env)
fun createOpenAIClient(apiUrl: String?, token: String): OpenAI {
    val host = apiUrl?.let {
        val url = StringBuilder(apiUrl)
        if (!url.endsWith("/")) url.append("/")
        if (!url.endsWith("v1/")) url.append("v1/")
        OpenAIHost(url.toString())
    } ?: OpenAIHost.OpenAI
    return OpenAI(
        token,
        host = host,
        logging = LoggingConfig(
            logLevel = LogLevel.Info,
        ),
        httpClientConfig = {
            engine {
                dispatcher = Dispatchers.IO // https://github.com/aallam/openai-kotlin/issues/461
            }
            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        env.logger.debug { message }
                    }
                }
            }
        }
    )
}

interface ChatCompletionCall : EnvHolder {
    val client: OpenAI
    val model: String
    override val env: Env

    context(_: Raise<ChatCompletionCallError>)
    suspend fun <T> chat(
        prompt: String,
        message: String,
        parseLLM: suspend (String) -> T,
    ): T
}

context(_: Raise<ChatCompletionCallError>)
suspend fun ChatCompletionCall.chatRaw(
    prompt: String,
    message: String
): String = chat(
    prompt = prompt,
    message = message,
    parseLLM = { it },
)

context(env: Env, _: Raise<ChatCompletionCallError>)
suspend fun ChatCompletionCall(
    apiUrl: String?,
    token: String,
    model: String,
    useStreamApi: Boolean = false,
    maxRetry: Int = MAX_RETRY,
    strict: Boolean = false, // if validate model exists
): ChatCompletionCall {
    val client = createOpenAIClient(apiUrl, token)
    return ChatCompletionCall(
        client = client,
        model = model,
        useStreamApi = useStreamApi,
        maxRetry = maxRetry,
        strict = strict,
    )
}

context(env: Env, _: Raise<ChatCompletionCallError>)
suspend fun ChatCompletionCall(
    client: OpenAI,
    model: String,
    useStreamApi: Boolean = false,
    maxRetry: Int = MAX_RETRY,
    strict: Boolean = true, // if validate model exists
): ChatCompletionCall {
    if (strict) {
        val models = runCatching { client.models() }.getOrElse {
            raise(ChatCompletionCallError.UnvalidatedApi("Try request models, but it cannot respond correctly."))
        }
        ensure(model in models.map { it.id.id }) {
            ChatCompletionCallError.ModelNotFound(model)
        }
    }
    return ChatCompletionCallImpl(
        client = client,
        model = model,
        useStreamApi = useStreamApi,
        env = env,
        maxRetry = maxRetry,
    )
}

class ChatCompletionCallImpl internal constructor(
    override val client: OpenAI,
    override val model: String,
    override val env: Env,
    val useStreamApi: Boolean = false,
    val maxRetry: Int = MAX_RETRY
) : ChatCompletionCall {
    context(_: Raise<ChatCompletionCallError>)
    override suspend fun <T> chat(
        prompt: String,
        message: String,
        parseLLM: suspend (String) -> T,
    ): T {
        val request = ChatCompletionRequest(
            model = ModelId(model),
            responseFormat = ChatResponseFormat.Text,
            messages = listOf(
                ChatMessage(role = ChatRole.System, content = prompt),
                ChatMessage(role = ChatRole.User, content = message)
            ),
            streamOptions = if (useStreamApi) StreamOptions(true) else null
        )
        var llmRetry = 0
        while (llmRetry < maxRetry) {
            val llmResult = runCatching {
                if (useStreamApi) client.chatCompletions(request)
                    .onEach {
                        noticeTokenConsume(it.usage)
                    }
                    .mapNotNull { it.choices.firstOrNull() }
                    .mapNotNull { it.delta }
                    .fold(StringBuilder()) { acc, e -> e.content?.let { acc.append(it) } ?: acc }
                    .toString()
                else client.chatCompletion(request).also {
                    noticeTokenConsume(it.usage)
                }.choices.first().message.content!!
            }.getOrElse { e ->
                if (e is OpenAIHttpException || e is OpenAITimeoutException || e is GenericIOException) {
                    env.logger.error { "API error: ${e.message}. Retry ${llmRetry + 1}/$MAX_RETRY." }
                    llmRetry++
                    continue
                } else throw e
            }
            return runCatching { parseLLM(llmResult) }.getOrElse { e ->
                llmRetry++
                env.logger.error { "LLM response parse failed (${llmRetry}/$MAX_RETRY): ${e.message}. Retrying..." }
                continue
            }
        }
        raise(ChatCompletionCallError.RetryTooMuch(maxRetry))
    }

    override fun toString() = "OpenAICall($model)"
}


private fun EnvHolder.noticeTokenConsume(usage: Usage?) {
    usage?.totalTokens?.let { tc ->
        logger.sign<AiSign>({ AiSign.ConsumeToken(tc) })
    }
}
