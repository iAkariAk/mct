package mct.gui

import com.aallam.openai.api.logging.LogLevel
import com.aallam.openai.client.LoggingConfig
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIHost
import kotlinx.coroutines.Dispatchers

var openAIClient: OpenAI? = null

fun createOpenAIClient(apiUrl: String?, token: String): OpenAI {
    val host = apiUrl?.let {
        val url = StringBuilder(it)
        if (!url.endsWith("/")) url.append("/")
        if (!url.endsWith("v1/")) url.append("v1/")
        OpenAIHost(url.toString())
    } ?: OpenAIHost.OpenAI
    return OpenAI(
        token,
        host = host,
        logging = LoggingConfig(logLevel = LogLevel.None),
        httpClientConfig = {
            engine { dispatcher = Dispatchers.IO }
        }
    )
}

suspend fun listModels(client: OpenAI): List<String> =
    client.models().map { it.id.id }.sorted()

suspend fun listModels(apiUrl: String?, token: String): List<String> =
    listModels(createOpenAIClient(apiUrl, token))
