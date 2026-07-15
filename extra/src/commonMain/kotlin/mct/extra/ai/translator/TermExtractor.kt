package mct.extra.ai.translator

import arrow.atomic.AtomicBoolean
import arrow.core.raise.context.Raise
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import mct.EnvHolder
import mct.extra.ai.*
import mct.util.IO

typealias OnTermExtractCancel = (TermTable) -> Unit

data class TermExtractionPrompts(
    val targetLanguage: String = TranslationPrompts.targetLanguage,
    val literatureStyle: String = TranslationPrompts.literatureStyle,
    val mapInfo: MapInfo = MapInfo.None,
    val extraPrompts: String? = null
) {
    companion object Defaults {
        val Default = TermExtractionPrompts()
    }
}

class TermExtractor(
    val call: ChatCompletionCall,
    val tokenThreshold: Int = TOKEN_COUNT_THRESHOLD,
    val prompts: TermExtractionPrompts = TermExtractionPrompts.Default,
    val concurrency: Int = 1,
    defaultTerms: TermTable = emptyMap(),
) : EnvHolder by call {
    private val terms = defaultTerms.toMutableMap()

    context(_: Raise<ChatCompletionCallError>)
    suspend fun extract(
        source: Set<String>,
        onCancel: OnTermExtractCancel,
    ): TermTable {
        val prompt = buildTermExtractionPrompt(prompts)

        val chunks = source.chunkedByToken(tokenThreshold)
        coroutineScope {
            val cancelled = AtomicBoolean(false)
            chunks.asIterable().forEachConcurrently(
                concurrency,
                Dispatchers.IO,
                { terms.putAll(it) }
            ) { chunk, add ->
                try {
                    val message = chunk.joinToString("\n")
                    val subterms = call.chat(
                        prompt,
                        message,
                        {
                            Json.decodeFromString<TermTable>(it)
                        },
                        validate = { terms -> terms.all { (source, target) -> source.isNotBlank() && target.isNotBlank() } },
                    )
                    add(subterms)
                } catch (e: Throwable) {
                    if (e is CancellationException) logger.error { "Extraction was cancelled." }
                    else logger.error { "Extraction interrupted." }
                    try {
                        withContext(NonCancellable) {
                            if (cancelled.compareAndSet(false, true)) {
                                onCancel(terms)
                            }
                        }
                    } finally {
                        throw e
                    }
                }
            }
        }

        return terms
    }
}

