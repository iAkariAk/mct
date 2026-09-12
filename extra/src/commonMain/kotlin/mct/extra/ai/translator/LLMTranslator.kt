package mct.extra.ai.translator

import arrow.core.Option
import arrow.core.raise.Raise
import arrow.core.raise.context.raise
import arrow.core.raise.recover
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import mct.EnvHolder
import mct.MCTError
import mct.extra.ai.*
import mct.kit.TranslationMapping
import mct.model.patch.FormatKind
import mct.model.patch.validate
import mct.model.text.*
import mct.notify
import mct.util.*
import mct.util.IO
import mct.util.formatir.IRElement
import mct.util.formatir.IRList
import mct.util.formatir.decodeFromString
import mct.util.formatir.encodeToString

data class LLMTranslationError(val reason: ChatCompletionCallError) : TranslationError, MCTError by reason

typealias TermTable = Map<String, String>

data class LLMTranslationPrompts(
    val literatureStyle: String = Defaults.literatureStyle,
    val targetLanguage: String = Defaults.targetLanguage,
    val handleGradientAggressively: Boolean = Defaults.handleGradientAggressively,
    val mapInfo: MapInfo = Defaults.mapInfo,
    val extraPrompts: String? = Defaults.extraPrompts
) {
    companion object Defaults {
        val literatureStyle = """
        - 使用简洁自然的语言，轻小说风格。
        - 保持原文的情感色彩和语气。
        - 不要过度意译，忠实于原文含义。
        - 人名、地名使用目标语言中通行、自然且符合世界观的译名。
    """.trimIndent()
        const val targetLanguage = "简体中文"
        const val handleGradientAggressively = false
        val mapInfo = MapInfo.None
        val extraPrompts: String? = null

        val Default = LLMTranslationPrompts() // Always at least to wait the above initialization
    }
}


private fun TermTable.render() = entries.joinToString("\n") { (source, target) ->
    "${source.trim()} => ${target.trim()}"
}

typealias RequestLLMTranslation = suspend context(Raise<ChatCompletionCallError>) (
    count: Int, message: String, format: FormatKind,
    validate: (Pair<TermTable, List<String?>>) -> Boolean
) -> Pair<TermTable, List<String?>>

typealias OnLLMTranslationCancel = (terms: TermTable, salvaged: TranslationMapping) -> Unit

class LLMTranslator internal constructor(
    private val call: ChatCompletionCall,
    private val requestTranslation: RequestLLMTranslation,
    defaultTerms: TermTable,
    private val customizedPrompts: LLMTranslationPrompts = LLMTranslationPrompts.Default,
    private val tokenThreshold: Int = TOKEN_COUNT_THRESHOLD,
    val concurrency: Int = 1,
) : Translator {
    companion object {
        operator fun invoke(
            call: ChatCompletionCall,
            defaultTerms: TermTable = emptyMap(),
            customizedPrompts: LLMTranslationPrompts = LLMTranslationPrompts.Default,
            tokenThreshold: Int = TOKEN_COUNT_THRESHOLD,
            concurrency: Int = 1,
        ): LLMTranslator {
            return LLMTranslator(
                call,
                requestTranslation = { expectedSize, message, kind, validate ->
                    call.chat(
                        prompt = buildTranslationPrompt(kind, customizedPrompts),
                        message = message,
                        parseLLM = {
                            parseLLMResponse(it, expectedSize)
                        },
                        validate = validate,
                    )
                }, defaultTerms, customizedPrompts, tokenThreshold, concurrency
            )
        }
    }

    override val env get() = call.env

    override val terms: MutableMap<String, String> = defaultTerms.toMutableMap()

    private val mutex = Mutex()

    context(_: Raise<TranslationError>)
    override suspend fun translate(
        format: FormatKind,
        sources: List<String>,
        onCancel: (List<TranslationResult>) -> Unit,
    ): List<TranslationResult> = coroutineScope {
        val chunks = sources.map(::escapeEspecialUnicode).withIndex().chunkedByToken(tokenThreshold).toList()
        val totalChunkSize = chunks.size
        logger.info { "Starting translation: ${sources.size} sources → $totalChunkSize chunks, ${terms.size} existing terms, kind: $format" }
        val translated = MutableList<TranslationResult>(sources.size) { Untranslated }
        var completedChunks = 0

        suspend fun processChunk(chunkIndex: Int, chunk: List<IndexedValue<String>>) {
            val (untranslatable, translatableStrips) = chunk.stripsWithIndex(format)
            untranslatable.forEach { (index, value) ->
                translated[index] = Untranslatable
                logger.info { "Skip: ${value.original}" }
            }
            val strippedCount =
                translatableStrips.count { (_, strip) -> strip is ComponentStrip.Simplified }
            logger.debug { "Chunk $chunkIndex: ${strippedCount}/${translatableStrips.size} items were stripped to plain text; ${untranslatable.size}/${chunk.size} untranslatable items were skipped" }
            val chunkAsStr = chunk.joinToString("\n") { it.value }
            val termSnapshot = mutex.withLock { terms.toMap() }
            val availableTerms = termSnapshot.filter { (source, _) -> chunkAsStr.contains(source, true) }
            val message = buildString {
                if (availableTerms.isNotEmpty()) {
                    append(availableTerms.render())
                    appendLine()
                }
                appendLine("-- MCT-CLI:START --")
                translatableStrips.filter { it.value !is ComponentStrip.Untranslatable }
                    .map { (_, strip) ->
                        val str = strip.stripOrOriginal()
                        str.replace("\n", "↠mctnl↠")
                    }.forEachIndexed { i, text ->
                        appendLine("[${i}] $text")
                    }
            }
            logger.info { "Handling ${chunkIndex + 1} (total $totalChunkSize)" }

            val (appendTermsRaw, appendedTranslatedRaw) = recover({
                requestTranslation(
                    translatableStrips.size,
                    message,
                    format
                ) { (_, result) ->
                    val invalidated = result.withIndex().filter { (stripsIndex, value) ->
                        translatableStrips[stripsIndex].value is ComponentStrip.CannotStrip && value?.let {
                            it.isNotEmpty() && !format.validate(it)
                        } ?: false
                    }
                    if (invalidated.isNotEmpty()) {
                        env.logger.info {
                            "LLM responds invalidly (${format.name}) ${
                                invalidated.joinToString("\n") {
                                    "${it.index}: ${it.value}; (original: ${chunk[it.index]}, strip: ${translatableStrips[it.index]})"
                                }
                            }"
                        }
                        false
                    } else true
                }
            }, {
                raise(LLMTranslationError(it))
            })
            val appendTerms = appendTermsRaw.map { (key, value) ->
                unescapeEspecialUnicode(key) to unescapeEspecialUnicode(value)
            }
            val appendedTranslated = translatableStrips.destrip(appendedTranslatedRaw).map {
                val value = when (val result = it.value) {
                    is TranslationResult.Translated -> TranslationResult.Translated(result.content.let(::unescapeEspecialUnicode))
                    else -> result
                }
                it.copy(value = value)
            }
            logger.info { "Handled ${chunkIndex + 1} (total $totalChunkSize)" }
            logger.debug {
                translatableStrips.zip(appendedTranslated).joinToString("\n") { (x, y) ->
                    val original = unescapeEspecialUnicode(x.value.original)
                    when (val result = y.value) {
                        is TranslationResult.Translated -> "Translate: $original => ${result.content}"
                        Untranslated -> "Cannot translate: $original"
                        Untranslatable -> unreachable
                    }
                }
            }
            val pct = mutex.withLock {
                terms += appendTerms
                appendedTranslated.forEach { (sourceIndex, translation) ->
                    translated[sourceIndex] = translation
                }
                (++completedChunks).toFloat() / totalChunkSize
            }
            notifier.notify<TranslateSign> { TranslateSign.Progress(pct) }
        }

        chunks.withIndex().forEachConcurrently<IndexedValue<MutableList<IndexedValue<String>>>, Unit>(
            concurrency,
            Dispatchers.IO,
            { _ -> },
        ) { (chunkIndex, chunk), _ ->
            try {
                processChunk(chunkIndex, chunk)
            } catch (e: Throwable) {
                if (e is CancellationException) logger.error { "Translation was cancelled." }
                else logger.error { "Translation interrupted." }
                try {
                    withContext(NonCancellable) {
                        onCancel(translated)
                    }
                } finally {
                    throw e
                }
            }
        }

        logger.info { "Translation complete: ${translated.size} items, ${terms.size} terms accumulated" }
        translated
    }

    override fun toString() = "Translator($call, $customizedPrompts)"
    override fun close() = call.close()
}


internal sealed interface ComponentStrip {
    val original: String

    data class CannotStrip(override val original: String) : ComponentStrip
    data class NoComponent(override val original: String) : ComponentStrip
    data class Untranslatable(override val original: String) : ComponentStrip
    data class Simplified(
        override val original: String,
        val sourceFormat: FormatKind,
        val source: SingleTextComponent<*>,
        val strip: String,
        val isSingleList: Boolean = false,
    ) : ComponentStrip {
        init {
            check(sourceFormat != PlainStr)
        }
    }
}

private fun ComponentStrip.stripOrOriginal() = when (this) {
    is ComponentStrip.Untranslatable -> original
    is ComponentStrip.CannotStrip -> original
    is ComponentStrip.NoComponent -> original
    is ComponentStrip.Simplified -> strip
}

context(env: EnvHolder)
internal fun String.strip(format: FormatKind): ComponentStrip {
    val raw = this
    fun cannotStrip() = null.also {
        env.logger.warning { "Cannot strip $raw" }
    }

    var isList = false
    val component = Option.catch {
        when (format) {
            PlainStr -> null
            else -> IRElement.decodeFromString(format, raw)
        }?.let {
            if (it is IRList) {
                it.takeIf { it.size == 1 }?.first()?.also { isList = true } ?: return ComponentStrip.CannotStrip(raw)
            } else it
        }?.decodeToCompound()
    }.getOrNull() ?: return ComponentStrip.NoComponent(raw)

    if (!component.hasHumbleReadableText()) return ComponentStrip.Untranslatable(raw)
    val single = component as? SingleTextComponent<*> ?: return ComponentStrip.CannotStrip(raw)

    val strip = (if (single.extra == null) {
        when (single) {
            is TextComponent.Plain -> single.text
            else -> cannotStrip()
        }
    } else cannotStrip()) ?: return ComponentStrip.CannotStrip(raw)
    return ComponentStrip.Simplified(raw, format, single, strip, isList)
}

@Suppress("UNCHECKED_CAST")
context(env: EnvHolder)
internal fun List<IndexedValue<String>>.stripsWithIndex(format: FormatKind): Pair<List<IndexedValue<ComponentStrip.Untranslatable>>, List<IndexedValue<ComponentStrip>>> =
    asSequence()
        .map { IndexedValue(it.index, it.value.strip(format)) }
        .let {
            val first = ArrayList<IndexedValue<ComponentStrip.Untranslatable>>()
            val second = ArrayList<IndexedValue<ComponentStrip>>()
            for (element in it) {
                if (element.value is ComponentStrip.Untranslatable) {
                    first.add(element as IndexedValue<ComponentStrip.Untranslatable>)
                } else {
                    second.add(element)
                }
            }
            Pair(first, second)
        }


context(env: EnvHolder)
internal fun List<String>.strips(format: FormatKind): Pair<List<ComponentStrip.Untranslatable>, List<ComponentStrip>> =
    asSequence()
        .map { it.strip(format) }
        .asIterable()
        .partition<ComponentStrip, ComponentStrip.Untranslatable>()

internal fun List<IndexedValue<ComponentStrip>>.destrip(response: List<String?>): List<IndexedValue<TranslationResult>> =
    zip(response).map { (iv, s) ->
        val (index, cs) = iv
        IndexedValue(index, cs.destrip(s))
    }


internal fun ComponentStrip.destrip(response: String?) = response?.let {
    when (this) {
        is ComponentStrip.Simplified -> {
            val str = when (sourceFormat) {
                PlainStr -> unreachable
                else -> {
                    val ir = source.replaceText(response).encodeToIR().let { e ->
                        if (isSingleList) IRList(e) else e
                    }
                    ir.encodeToString(sourceFormat)
                }
            }
            TranslationResult.Translated(str)
        }

        is ComponentStrip.CannotStrip -> TranslationResult.Translated(response)
        is ComponentStrip.NoComponent -> TranslationResult.Translated(response)
        is ComponentStrip.Untranslatable -> TranslationResult.Untranslatable
    }
} ?: TranslationResult.Untranslated

private val LINE_PREFIX = Regex2("""^\[(\d+)]\s*""")
private val REGEX_LLM_OUTPUT =
    """(?s)^-- MCT-CLI:TRANSLATED --\n(.*?)\n-- MCT-CLI:TERMS --\n(.*?)(?:\n-- MCT-CLI:END --)?\s*$""".toRegex2()

internal fun parseLLMResponse(content: String, expectedSize: Int): Pair<TermTable, List<String?>> {
    val (appendedTranslated, appendTermsStr) = REGEX_LLM_OUTPUT.matchEntire(content)?.destructured
        ?: error("LLM responses invalidly: $content")
    val appendTerms = runCatching { Json.decodeFromString<TermTable>(appendTermsStr) }.getOrNull().orEmpty()
    val lines = appendedTranslated.lines()
        .asSequence()
        .mapNotNull { line ->
            val num = LINE_PREFIX.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
            val text = LINE_PREFIX.replaceFirst(line, "")
                .replace("↠mctnl↠", "\n")
            IndexedValue(num, text)
        }
        .pad(expectedSize)
    return appendTerms to lines
}

private fun Sequence<IndexedValue<String>>.pad(expectedSize: Int): List<String?> {
    val list = MutableList<String?>(expectedSize) { null }
    sortedBy { it.index }.forEach { (i, v) ->
        list[i] = v
    }
    return list
}

