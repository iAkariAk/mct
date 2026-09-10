package mct.extra.ai.translator

import arrow.core.Option
import arrow.core.raise.Raise
import arrow.core.raise.context.raise
import arrow.fx.coroutines.parMap
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.schema.json.serializers.toJsonElements
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import mct.Env
import mct.LoggerHolder
import mct.command.MCCommandJson
import mct.logger
import mct.model.patch.FormatKind
import mct.model.text.*
import mct.serializer.Snbt
import mct.util.decodeFromString
import mct.util.encodeToString
import mct.util.formatir.toIR
import mct.util.formatir.toJsonElement
import mct.util.formatir.toNbtTag
import mct.util.unreachable
import net.benwoodworth.knbt.NbtTag

sealed interface ApiTranslationError : TranslationError {
    data class RetryTooMuch(val maxRetry: Int, val exceptions: List<Exception>) : ApiTranslationError {
        override val message =
            "Retry too much, exceeding max retry $maxRetry; Exceptions: ${exceptions.joinToString("\n---\n") { it.message ?: "<null>" }}"
    }
}

sealed class TranslationApi(protected val maxRetry: Int, val from: String?, val to: String) : AutoCloseable {
    abstract class SingleTranslation(maxRetry: Int, from: String?, to: String) : TranslationApi(maxRetry, from, to) {
        context(_: Raise<ApiTranslationError>, _: LoggerHolder)
        abstract suspend fun translateWithoutRetry(text: String): String

        context(_: Raise<ApiTranslationError>, _: LoggerHolder)
        suspend fun translate(text: String): String = retryWhenExceptionThrown { translateWithoutRetry(text) }

    }

    abstract class BatchTranslation(maxRetry: Int, sourceLanguage: String?, targetLanguage: String) :
        TranslationApi(maxRetry, sourceLanguage, targetLanguage) {
        context(_: Raise<ApiTranslationError>, _: LoggerHolder)
        abstract suspend fun translateWithoutRetry(texts: List<String>): List<String>

        context(_: Raise<ApiTranslationError>, _: LoggerHolder)
        suspend fun translate(texts: List<String>): List<String> =
            retryWhenExceptionThrown { translateWithoutRetry(texts) }
    }

    context(_: Raise<ApiTranslationError>, _: LoggerHolder)
    internal inline fun <R> retryWhenExceptionThrown(
        action: () -> R,
    ): R {
        val exceptions = mutableListOf<Exception>()
        repeat(maxRetry) {
            try {
                return action()
            } catch (e: Exception) {
                if (e is CancellationException) {
                    throw e
                }
                logger.error { "Retry because ${e.message}" }
                exceptions.add(e)
            }
        }
        raise(ApiTranslationError.RetryTooMuch(maxRetry, exceptions))
    }
}

object TranslationApis {
    // https://github.com/xxnuo/MTranServer/blob/main/API.md
    class MTranServerTranslation(
        val apiUrl: String,
        private val token: String? = null,
        maxRetry: Int = Translator.MAX_RETRY_COUNT,
        sourceLanguage: String?,
        targetLanguage: String
    ) : TranslationApi.BatchTranslation(maxRetry, sourceLanguage, targetLanguage) {
        private val client = HttpClient {
            defaultRequest {
                token?.let {
                    headers.append("Authorization", "Bearer $token")
                }
            }
            install(ContentNegotiation) {
                json()
            }

            install(HttpTimeout) {
                connectTimeoutMillis = 600000
                requestTimeoutMillis = 600000
                connectTimeoutMillis = 600000
            }
        }

        override fun close() = client.close()

        context(_: Raise<ApiTranslationError>, _: LoggerHolder)
        override suspend fun translateWithoutRetry(texts: List<String>): List<String> {
            val response = client.post("$apiUrl/translate/batch") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("from", JsonPrimitive(from ?: "auto"))
                    put("to", JsonPrimitive(to))
                    put("texts", JsonArray(texts.toJsonElements()))
                    put("html", JsonPrimitive(false))
                })
            }
            val responseBody = response.body<JsonObject>()
            return responseBody["results"]!!.jsonArray.map { it.jsonPrimitive.content }
        }
    }
}

context(_: Raise<ApiTranslationError>, _: LoggerHolder)
suspend fun TranslationApi.translate(texts: List<String>): List<String> = when (this) {
    is TranslationApi.BatchTranslation -> translate(texts)
    is TranslationApi.SingleTranslation -> texts.parMap { translate(it) }
}

class ApiTranslator(private val api: TranslationApi, override val env: Env) : Translator {
    override val terms = mutableMapOf<String, String>() // unused

    context(_: Raise<TranslationError>)
    override suspend fun translate(
        format: FormatKind,
        sources: List<String>,
        onCancel: (List<TranslationResult>) -> Unit
    ): List<TranslationResult> {
        val trim = sources.trimComponents(format)

        val expendedSizes = IntArray(sources.size)
        val translatableTexts = trim.flatMapIndexed { index, trim ->
            when (trim) {
                is Untranslatable -> emptyList()
                is Failure -> {
                    expendedSizes[index] = 1
                    listOf(trim.original)
                }

                is Success -> {
                    val expended = trim.texts
                    expendedSizes[index] = expended.size
                    expended
                }
            }
        }
        val translatedTranslatableTexts = api.translate(translatableTexts)
        val translated = MutableList<TranslationResult>(sources.size) { Untranslated }
        var expendedIndex = 0
        for (index in sources.indices) {
            val windowSize = expendedSizes[index]
            if (windowSize > 0) {
                val trim = trim[index]
                when (trim) {
                    is Untranslatable -> unreachable
                    is Failure -> {
                        check(windowSize == 1)
                        val text = translatedTranslatableTexts[expendedIndex]
                        translated[index] = TranslationResult.Translated(text)
                    }

                    is Success -> {
                        val texts = translatedTranslatableTexts.subList(expendedIndex, expendedIndex + windowSize)
                        var cursor = 0
                        val translatedComponent = trim.component.transformText {
                            texts[cursor++]
                        }
                        val encodedComponent = when (format) {
                            JsonStr, JsonObj -> MCCommandJson.encodeToString(
                                translatedComponent.toIR().toJsonElement()
                            )

                            SnbtStr, Nbt -> Snbt.encodeToString<NbtTag>(translatedComponent.toIR().toNbtTag())
                            PlainStr -> unreachable
                        }
                        translated[index] = TranslationResult.Translated(encodedComponent)

                    }
                }
                expendedIndex += windowSize
            }
        }
        return translated
    }

    override fun close() = api.close()
}

internal fun List<String>.trimComponents(format: FormatKind): List<ComponentTrim> =
    map { it.trimComponent(format) }

internal fun String.trimComponent(format: FormatKind): ComponentTrim {
    val raw = this
    val component = Option.catch {
        when (format) {
            JsonStr, JsonObj -> MCCommandJson.decodeFromString<JsonElement>(raw).toIR()
            SnbtStr, Nbt -> Snbt.decodeFromString<NbtTag>(raw).toIR()
            PlainStr -> null
        }?.decodeToCompound()
    }.getOrNull() ?: return ComponentTrim.Failure(raw)
    if (!component.hasHumbleReadableText()) return ComponentTrim.Untranslatable(raw)
    val texts = component.flattenText()
    return ComponentTrim.Success(component, format, texts)
}

internal sealed interface ComponentTrim {
    data class Success(
        val component: TextComponent<*>,
        val format: FormatKind,
        val texts: List<String>
    ) : ComponentTrim

    data class Failure(val original: String) : ComponentTrim
    data class Untranslatable(val original: String) : ComponentTrim
}
