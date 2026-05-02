package mct.extra.translator

import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatResponseFormat
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.exception.OpenAIHttpException
import com.aallam.openai.api.exception.OpenAITimeoutException
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.mapNotNull
import mct.Env

private const val MAX_RETRY = 20

context(env: Env)
internal suspend fun OpenAI.chatRaw(
    model: String,
    prompt: String,
    message: String,
    useStreamApi: Boolean = false,
    maxRetry: Int = MAX_RETRY
): String = chat(
    model = model,
    prompt = prompt,
    message = message,
    parseLLM = { it },
    useStreamApi = useStreamApi,
    maxRetry = maxRetry
)

context(env: Env)
internal suspend fun <T> OpenAI.chat(
    model: String,
    prompt: String,
    message: String,
    useStreamApi: Boolean = false,
    parseLLM: suspend (String) -> T,
    maxRetry: Int = MAX_RETRY
): T {
    val request = ChatCompletionRequest(
        model = ModelId(model),
        responseFormat = ChatResponseFormat.Text,
        messages = listOf(
            ChatMessage(role = ChatRole.System, content = prompt),
            ChatMessage(role = ChatRole.User, content = message)
        ),
    )
    var llmRetry = 0
    while (llmRetry < maxRetry) {
        val llmResult = runCatching {
            if (useStreamApi) chatCompletions(request)
                .mapNotNull { it.choices.firstOrNull() }
                .mapNotNull { it.delta }
                .fold(StringBuilder()) { acc, e -> e.content?.let { acc.append(it) } ?: acc }
                .toString()
            else chatCompletion(request).choices.first().message.content!!
        }.getOrElse { e ->
            if (e is OpenAIHttpException || e is OpenAITimeoutException) {
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
    error("LLM response consistently malformed after $MAX_RETRY retries")
}
