package mct.extra.ai.translator

import arrow.atomic.AtomicBoolean
import arrow.core.Option
import arrow.core.raise.Raise
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import mct.EnvHolder
import mct.extra.ai.*
import mct.kit.TranslationMapping
import mct.model.patch.*
import mct.notify
import mct.serializer.MCTJson
import mct.serializer.Snbt
import mct.text.*
import mct.util.IO
import mct.util.Regex2
import mct.util.destructured
import mct.util.formatir.IRList
import mct.util.formatir.toIR
import mct.util.formatir.toJsonElement
import mct.util.formatir.toNbtTag
import mct.util.toRegex2
import mct.util.toSnbt
import net.benwoodworth.knbt.NbtList
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
    count: Int, message: String, kind: FormatKind,
    validate: (Pair<TermTable, List<String?>>) -> Boolean
) -> Pair<TermTable, List<String?>>

typealias OnTranslateCancel = (terms: TermTable, salvaged: TranslationMapping) -> Unit

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
        kind: FormatKind,
        sources: List<String>,
        onCancel: (List<String?>) -> Unit = {},
    ): List<String> = coroutineScope {
        val chunks = sources.withIndex().chunkedByToken(tokenThreshold).toList()
        val totalChunkSize = chunks.size
        logger.info { "Starting translation: ${sources.size} sources → $totalChunkSize chunks, ${terms.size} existing terms, kind: $kind" }
        val translated = MutableList<String?>(sources.size) { null }
        var completedChunks = 0

        suspend fun processChunk(chunkIndex: Int, chunk: List<IndexedValue<String>>) {
            val strips = chunk.stripWithIndex(kind)
            val strippedCount = strips.count { (_, strip) -> strip is CompoundStrip.Simplified ||strip is CompoundStrip.Untranslatable }
            logger.debug { "Chunk $chunkIndex: ${strippedCount}/${strips.size} items stripped to plain text" }
            val chunkAsStr = chunk.joinToString("\n") { it.value }
            val termSnapshot = mutex.withLock { terms.toMap() }
            val availableTerms = termSnapshot.filter { (source, _) -> chunkAsStr.contains(source, true) }
            val message = buildString {
                if (availableTerms.isNotEmpty()) {
                    append(availableTerms.render())
                    appendLine()
                }
                appendLine("-- MCT-CLI:START --")
                strips.map { (_, strip) ->
                    val str = strip.stripOrOriginal()
                    str.replace("\n", "↠mctnl↠")
                }.forEachIndexed { i, text ->
                    appendLine("[${i}] $text")
                }
            }
            logger.info { "Handling ${chunkIndex + 1} (total $totalChunkSize)" }


            val (appendTerms, appendedTranslatedRaw) = requestTranslation(strips.size, message, kind) { (_, result) ->
                val invalidated = result.withIndex().filter { (stripsIndex, value) ->
                    strips[stripsIndex].value is CompoundStrip.CannotStrip && value?.let {
                        it.isNotEmpty() && !kind.validate(it)
                    } ?: false
                }
                if (invalidated.isNotEmpty()) {
                    env.logger.info {
                        "LLM responds invalidly (${kind.name}) ${
                            invalidated.joinToString("\n") {
                                "${it.index}: ${it.value}; (original: ${chunk[it.index]}, strip: ${strips[it.index]})"
                            }
                        }"
                    }
                    false
                } else true
            }
            val appendedTranslated = strips.destrip(appendedTranslatedRaw)
            logger.info { "Handled ${chunkIndex + 1} (total $totalChunkSize)" }
            logger.debug {
                chunk.zip(appendedTranslated).joinToString("\n") { (x, y) -> "Translate ${x.value} => ${y.value}" }
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
        @Suppress("UNCHECKED_CAST")
        translated as List<String> // Safely
    }

    override fun toString() = "Translator($call, $customizedPrompts)"
}


internal sealed interface CompoundStrip {
    val original: String

    data class CannotStrip(override val original: String) : CompoundStrip
    data class NoCompound(override val original: String) : CompoundStrip
    data class Untranslatable(override val original: String) : CompoundStrip
    data class Simplified(
        override val original: String,
        val sourceFormat: FormatKind,
        val source: TextCompound,
        val strip: String,
        val isSingleList: Boolean = false,
    ) : CompoundStrip
}

private fun CompoundStrip.stripOrOriginal() = when (this) {
    is CompoundStrip.Untranslatable -> original
    is CompoundStrip.CannotStrip -> original
    is CompoundStrip.NoCompound -> original
    is CompoundStrip.Simplified -> strip
}

context(env: EnvHolder)
internal fun String.strip(kind: FormatKind): CompoundStrip {
    val raw = this
    fun cannotStrip() = null.also {
        env.logger.warning { "Cannot strip $raw" }
    }

    var isList = false
    val compound = Option.catch {
        when (kind) {
            JsonStr, JsonObj -> MCTJson.decodeFromString<JsonElement>(raw).let {
                if (it is JsonArray) {
                    it.takeIf { it.size == 1 }?.first()?.also { isList = true }.bind()
                } else it
            }.toIR()

            SnbtStr, Nbt -> Snbt.decodeFromString<NbtTag>(raw).let {
                if (it is NbtList<*>) {
                    it.takeIf { it.size == 1 }?.first()?.also { isList = true }.bind()
                } else it
            }.toIR()

            PlainStr -> null
        }?.decodeToCompound()
    }.getOrNull() ?: return CompoundStrip.NoCompound(raw)

    if (!compound.hasText()) return CompoundStrip.Untranslatable(raw)

    val strip = (if (compound.extra.isEmpty()) {
        when (compound) {
            is TextCompound.Plain -> compound.text
            else -> cannotStrip()
        }
    } else cannotStrip()) ?: return CompoundStrip.CannotStrip(raw)
    return CompoundStrip.Simplified(raw, kind, compound, strip, isList)
}

context(env: EnvHolder)
internal fun List<IndexedValue<String>>.stripWithIndex(kind: FormatKind): List<IndexedValue<CompoundStrip>> =
    map { IndexedValue(it.index, it.value.strip(kind)) }

context(env: EnvHolder)
internal fun List<String>.strip(kind: FormatKind): List<CompoundStrip> = map { it.strip(kind) }

internal fun List<IndexedValue<CompoundStrip>>.destrip(response: List<String?>): List<IndexedValue<String>> =
    zip(response).map { (iv, s) ->
        val (index, cs) = iv
        val r = s?.let {
            when (cs) {
                is CompoundStrip.Simplified -> {
                    when (cs.sourceFormat) {
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
                }

                is CompoundStrip.CannotStrip -> s
                is CompoundStrip.NoCompound -> s
                is CompoundStrip.Untranslatable -> s
            }
        } ?: cs.original
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
): TranslationMapping = coroutineScope {
    if (groups.isEmpty()) {
        logger.debug { "Skipping empty group" }
        return@coroutineScope emptyMap()
    }
    val extractions = groups.flatMap { it.extractions }.groupBy {
        when (it) {
            is DatapackExtraction.MCJson -> FormatKind.JsonStr
            is DatapackExtraction.MCFunction -> FormatKind.PlainStr
            is RegionExtraction -> it.nbt.kind
            is DatapackExtraction.Nbt -> it.nbt.kind
        }
    }
    val mapping = mutableMapOf<String, String?>()
    val mappingMutex = Mutex()

    suspend fun execute(block: suspend (append: suspend (Iterable<Pair<String, String?>>) -> Unit) -> Unit) {
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

    val cancelled = AtomicBoolean(false)
    extractions.forEach { (kind, extractions) ->
        execute { append ->
            val sources = extractions.asSequence().flatMap {
                it.contents().filter(String::isNotBlank)
            }.distinct().filter { it !in caches }.toList()
            val translated = translate(kind, sources) { translated ->
                val salvaged = buildMap<String, String?> {
                    translated.forEachIndexed { index, translated ->
                        translated?.let {
                            put(sources[index], translated)
                        }
                    }
                }
                if (cancelled.compareAndSet(false, true)) {
                    onCancel(terms, mapping + salvaged)
                }
            }
            append(sources.zip(translated))
        }
    }
    mapping.toMutableMap().putAll(caches)
    notifier.notify<TranslateSign> { TranslateSign.Progress(1f) }
    env.logger.info { "Built mapping with ${mapping.size} entries" }
    mapping
}

