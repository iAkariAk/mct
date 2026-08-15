@file:OptIn(InternalCoroutinesApi::class)

package mct.extra.ai.translator

import arrow.core.Option
import arrow.core.raise.Raise
import kotlinx.coroutines.*
import kotlinx.coroutines.internal.SynchronizedObject
import kotlinx.coroutines.internal.synchronized
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import mct.EnvHolder
import mct.command.MCCommandJson
import mct.extra.ai.*
import mct.kit.TranslationMapping
import mct.model.patch.ExtractionGroup
import mct.model.patch.FormatKind
import mct.model.patch.contentsWithFormat
import mct.model.patch.validate
import mct.model.text.*
import mct.notify
import mct.serializer.MCTJson
import mct.serializer.Snbt
import mct.util.*
import mct.util.IO
import mct.util.formatir.IRList
import mct.util.formatir.toIR
import mct.util.formatir.toJsonElement
import mct.util.formatir.toNbtTag
import net.benwoodworth.knbt.NbtTag


typealias TermTable = Map<String, String>

data class TranslationPrompts(
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

        val Default = TranslationPrompts() // Always at least to wait the above initialization
    }
}


private fun TermTable.render() = entries.joinToString("\n") { (source, target) ->
    "${source.trim()} => ${target.trim()}"
}

typealias RequestTranslation = suspend context(Raise<ChatCompletionCallError>)(
    count: Int, message: String, format: FormatKind,
    validate: (Pair<TermTable, List<String?>>) -> Boolean
) -> Pair<TermTable, List<String?>>

typealias OnTranslateCancel = (terms: TermTable, salvaged: TranslationMapping) -> Unit

sealed interface TranslationResult {
    data object Untranslated : TranslationResult
    data object Untranslatable : TranslationResult
    data class Translated(val content: String) : TranslationResult
}

class Translator internal constructor(
    private val call: ChatCompletionCall,
    private val requestTranslation: RequestTranslation,
    defaultTerms: TermTable,
    private val customizedPrompts: TranslationPrompts = TranslationPrompts.Default,
    private val tokenThreshold: Int = TOKEN_COUNT_THRESHOLD,
    val concurrency: Int = 1,
) : EnvHolder {
    companion object {
        operator fun invoke(
            call: ChatCompletionCall,
            defaultTerms: TermTable = emptyMap(),
            customizedPrompts: TranslationPrompts = TranslationPrompts.Default,
            tokenThreshold: Int = TOKEN_COUNT_THRESHOLD,
            concurrency: Int = 1,
        ): Translator {
            return Translator(
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

    val terms: MutableMap<String, String> = defaultTerms.toMutableMap()

    private val mutex = Mutex()

    context(_: Raise<ChatCompletionCallError>)
    suspend fun translate(
        format: FormatKind,
        sources: List<String>,
        onCancel: (List<TranslationResult>) -> Unit = {},
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


            val (appendTermsRaw, appendedTranslatedRaw) = requestTranslation(
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
    ) : ComponentStrip
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
            JsonStr, JsonObj -> MCCommandJson.decodeFromString<JsonElement>(raw).toIR()
            SnbtStr, Nbt -> Snbt.decodeFromString<NbtTag>(raw).toIR()
            PlainStr -> null
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
        val r = s?.let {
            when (cs) {
                is ComponentStrip.Simplified -> {
                    val str = when (cs.sourceFormat) {
                        FormatKind.PlainStr -> s
                        else -> {
                            val ir = cs.source.replaceText(s).encodeToIR().let { e ->
                                if (cs.isSingleList) IRList(e) else e
                            }
                            when (cs.sourceFormat) {
                                JsonStr, JsonObj -> MCTJson.encodeToString(ir.toJsonElement())
                                SnbtStr, Nbt -> ir.toNbtTag().toSnbt(false)
                            }
                        }
                    }
                    TranslationResult.Translated(str)
                }

                is ComponentStrip.CannotStrip -> TranslationResult.Translated(s)
                is ComponentStrip.NoComponent -> TranslationResult.Translated(s)
                is ComponentStrip.Untranslatable -> TranslationResult.Untranslatable
            }
        } ?: TranslationResult.Untranslated
        IndexedValue(index, r)
    }

private val LINE_PREFIX = Regex2("""^\[(\d+)]\s*""")

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

private val REGEX_LLM_OUTPUT =
    """(?s)^-- MCT-CLI:TRANSLATED --\n(.*?)\n-- MCT-CLI:TERMS --\n(.*?)(?:\n-- MCT-CLI:END --)?\s*$""".toRegex2()


context(_: Raise<ChatCompletionCallError>)
suspend fun Translator.translate(
    groups: List<ExtractionGroup>,
    caches: TranslationMapping = emptyMap(),
    concurrentByKind: Boolean = false,
    onCancel: OnTranslateCancel = { _, _ -> },
): TranslationMapping {
    if (groups.isEmpty()) {
        logger.debug { "Skipping empty group" }
        return emptyMap()
    }
    val extractions = mutableMapOf<FormatKind, MutableList<String>>()
    for ((key, second) in groups.flatMap { it.extractions.flatMap { it.contentsWithFormat() } }) {
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
    env.logger.info { "Built mapping with ${mapping.size} entries" }
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