package mct.extra.ai.translator

import arrow.core.raise.Raise
import kotlinx.coroutines.*
import kotlinx.coroutines.internal.SynchronizedObject
import kotlinx.coroutines.internal.synchronized
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mct.EnvHolder
import mct.MCTError
import mct.kit.TranslationMapping
import mct.model.patch.Extraction
import mct.model.patch.ExtractionGroup
import mct.model.patch.FormatKind
import mct.model.patch.contentsWithFormat
import mct.notify
import mct.util.IO

sealed interface TranslationError : MCTError

sealed interface TranslationResult {
    data object Untranslated : TranslationResult
    data object Untranslatable : TranslationResult
    data class Translated(val content: String) : TranslationResult
}

interface Translator : EnvHolder, AutoCloseable {
    companion object {
        const val MAX_RETRY_COUNT = 20
    }

    val terms: MutableMap<String, String>

    context(_: Raise<TranslationError>)
    suspend fun translate(
        format: FormatKind,
        sources: List<String>,
        onCancel: (List<TranslationResult>) -> Unit = {},
    ): List<TranslationResult>
}


@OptIn(InternalCoroutinesApi::class)
context(_: Raise<TranslationError>)
suspend fun Translator.translate(
    groups: List<ExtractionGroup>,
    caches: TranslationMapping = emptyMap(),
    concurrentByKind: Boolean = false,
    onCancel: OnLLMTranslationCancel = { _, _ -> },
): TranslationMapping {
    if (groups.isEmpty()) {
        logger.debug { "Skipping empty group" }
        return emptyMap()
    }
    val extractions = mutableMapOf<FormatKind, MutableList<String>>()
    for ((key, second) in groups.flatMap { it.extractions.flatMap(Extraction::contentsWithFormat) }) {
        val list = extractions.getOrPut(key) { ArrayList() }
        list.add(second)
    } // group by kind and map its value
    val mapping: MutableMap<String, String?> = mutableMapOf()
    val mappingMutex = Mutex()

    suspend fun CoroutineScope.execute(block: suspend (append: suspend (Map<String, String?>) -> Unit) -> Unit) {
        if (concurrentByKind) {
            launch(Dispatchers.IO) {
                block { others ->
                    mappingMutex.withLock {
                        mapping.putAll(others)
                    }
                }
            }
        } else block(mapping::putAll)
    }

    val salvages = mutableMapOf<String, String?>()
    val salvagesLock = SynchronizedObject()
    try {
        coroutineScope {
            extractions.forEach { (kind, extractions) ->
                execute { append ->
                    val sources = extractions.asSequence()
                        .filter(String::isNotBlank)
                        .distinct()
                        .filter { it !in caches }.toList()
                    val translated = translate(kind, sources) { translated ->
                        val salvaged = translated.export(sources)
                        synchronized(salvagesLock) {
                            salvages.putAll(salvaged)
                        }
                    }
                    append(translated.export(sources))
                }
            }
        }
    } catch (e: Throwable) {
        onCancel(terms, mapping + salvages)
        throw e
    }
    notifier.notify<TranslateSign> { TranslateSign.Progress(1f) }
    logger.info { "Built mapping with ${mapping.size} entries" }
    return mapping
}

private fun List<TranslationResult>.export(sources: List<String>) = buildMap {
    forEachIndexed { index, translated ->
        when (translated) {
            is TranslationResult.Translated -> put(sources[index], translated.content)
            Untranslatable -> put(sources[index], null)
            Untranslated -> {}
        }
    }
}