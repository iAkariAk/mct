package mct.extra.ai.translator

import arrow.core.Option
import arrow.core.raise.Raise
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import mct.*
import mct.extra.ai.ChatCompletionCall
import mct.extra.ai.ChatCompletionCallError
import mct.extra.ai.TOKEN_COUNT_THRESHOLD
import mct.extra.ai.chunkedByToken
import mct.serializer.MCTJson
import mct.serializer.Snbt
import mct.text.TextCompound
import mct.text.decodeToCompound
import mct.text.encodeToIR
import mct.text.replaceText
import mct.util.IO
import mct.util.formatir.IRList
import mct.util.formatir.toIR
import mct.util.formatir.toJsonElement
import mct.util.formatir.toNbtTag
import mct.util.toSnbt
import net.benwoodworth.knbt.NbtList
import net.benwoodworth.knbt.NbtTag


typealias TermTable = Set<Term>

@Serializable
data class Term(val source: String, val target: String, val type: TermType)

@Serializable
enum class TermType {
    @SerialName("name")
    Name,

    @SerialName("term")
    Term
}


data class CustomizedPrompts(
    val literatureStyle: String = Defaults.literatureStyle,
    val targetLanguage: String = Defaults.targetLanguage,
    val handleGradientAggressively: Boolean = Defaults.handleGradientAggressively,
) {
    companion object Defaults {
        val literatureStyle = """
        - 使用简洁自然的语言，轻小说风格。
        - 保持原文的情感色彩和语气。
        - 不要过度意译，忠实于原文含义。
        - 人名、地名使用日文汉字/中文习惯译名。
    """.trimIndent()

        const val targetLanguage = "简体中文"
        const val handleGradientAggressively = false

        val Default = CustomizedPrompts() // Always at least to wait the above initialization
    }
}


private fun prompt(kind: FormatKind, prompts: CustomizedPrompts): String =
    """你是一名专精 Minecraft 地图的翻译引擎。你的任务是将输入的文本翻译为${prompts.targetLanguage}，同时严格保护数据结构的完整性。

=== 输入协议 ===
- 输入采用 MCT-CLI 协议格式。
- "-- MCT-CLI:START --" 之前是术语表（JSON 格式），必须严格遵循其中的翻译映射，且术语表本身不要翻译。
- "-- MCT-CLI:START --" 之后是待翻译内容，每行以 "[N] " 开头（N 是行号），后跟该行的文本内容。
- 行内的 "\n"和"↠mctnl↠" 是转义换行符，不要还原为真实换行，也不要删去、插字。
- 行号 [N] 是该行的唯一标识，你输出的每行译文也必须以相同的 "[N] " 开头。

示例（例句翻译不影响目标语言，请始终翻译成${prompts.targetLanguage}）：
Kaguya => 辉夜姬
-- MCT-CLI:START --
[1] 待翻译文本行1
[2] 待翻译文本行2

=== 核心规则（优先级从高到低） ===

【规则 0 — 行数必须一致（最高优先级）】
- 输出的译文行数必须等于输入行数。
- 禁止合并多行为一行，禁止将一行拆分为多行。
- 顺序必须与输入完全一致。即使相邻行文本相似（如多个药水效果行），也禁止调换顺序——第N行译文必须严格对应第N行原文。
- 如果某行内容无需翻译（纯数字/符号/已为中文），原样保留该行。

【规则 1 — 数据结构保护】
- 内容可能是 JSON 、SNBT（Minecraft 文本组件）或者完整的Minecraft命令。
- 绝对禁止修改：字段名、键名、对象/数组结构、方括号/花括号/逗号、引号类型。
- 不得翻译Minecraft命令中的非文本内容（如spawner[]中spawner）。
- 转义序列（如 \n、\"、\\）必须原样保留。
- 输出必须是结构合法的 JSON/SNBT。
${if (prompts.handleGradientAggressively) "- 唯一例外：当启用渐变色激进策略时，允许对渐变文本组件的颜色数组进行插值调整（见规则3补充条款），其他结构保护条款仍然有效。" else ""}

【规则 2 — 翻译范围】
- 只翻译字符串值中的自然语言部分。
- 重点翻译 "text" 和 "fallback" 字段的内容。
- 不要翻译：键名、标识符、颜色代码（如 §a、§6）、格式化代码、枚举类值、Minecraft命令逻辑部分。

【规则 3 — 文本组件保护】
- 对于 Minecraft 的 "translate" + "with" 组件：可以调整 with 数组内元素的顺序以符合中文语序，但不能增减元素数量。${
        if (prompts.handleGradientAggressively) """
- **渐变色文本组件激进处理（仅当此条款被激活时生效）**  
渐变色组件通常由多个带 `color` 属性的 `text` 节点组成，形成颜色过渡效果。处理步骤如下：  
1. **正常翻译**：先翻译各节点内的文本，尽量保留原始字符数。  
2. **长度适配策略**（当译文总长度与原文差距过大，可能导致渐变断裂或视觉不协调时启用）：  
 - **译文过短**：允许进行 **语义扩写**，此时忽略其他节点数量限制，适度增加修饰词、重复关键语素或补充意境词，使译文长度接近原文（例如 "Frost Blade" → "霜寒刺骨之刃"）。  
   - 若扩写后仍显著短于原文，可 **删除部分中间颜色节点**（即从 `extra` 数组中移除若干过渡色片断），将渐变简化为更少的颜色阶梯，使短文本也能呈现平滑渐变。删除时只能移除完整节点，不得修改保留节点的 `color` 值或文本内容。  
   - 禁止直接保留原文不译，必须输出译文。  
 - **译文过长**：允许 **插入额外颜色插值节点**，将较长的译文拆分为更多文字片段，并为其补充中间色，以延伸渐变覆盖长度。插入时，新增节点的颜色应从相邻节点颜色插值计算得出（例如在两个 #FF0000 和 #0000FF 之间插入 #7F007F），保持视觉连贯。  
3. **长度参考**：中文单个字视觉宽度约等于英文2个字符。请以“视觉等宽”而非“字符数相等”来评估长度是否匹配。  
4. **错误容忍**：若调整后导致渐变效果不如原始，只要数据结构合法且译文准确，即视为成功。  
示例：  
- 原文渐变 "Legendary Frost Guardian"（23字符），译文 "永冬寒霜的传奇守护者"（11字，视觉约22字符），视为长度合格。  
- 若译文仅为 "霜卫"（2字，视觉4字符），则先扩写为 "永霜守护者"，仍不足时可删除部分颜色节点，最终输出简化的渐变。"""
        else ""
    }
- 对于其他单行富文本（text + extra 列表）：可以调整相邻文本节点的顺序以获得自然的中文表达，但样式属性（color、bold、italic 等）必须原封不动保留。
- 禁止跨越语义边界（如 clickEvent、hoverEvent 包裹的文本块）。
- 原行数必须与翻译后的行数完全对齐，不得跨行数调整语序。

【规则 4 — 字符串处理】
- 保持原有引号形式不变（双引号/单引号）。
- 不要修改任何转义字符。
- 如果替换后的文本包含特殊字符，不需要做额外转义（由程序处理）。

【规则 5 — 术语一致性】
- 严格遵循术语表中提供的翻译。
- 同一人物名、地名、物品名在整个翻译中保持统一。
- 不确定时优先一致性而非猜测。

【规则 6 — 人名、地名处理】
- 大骆驼、小骆驼、下划线、用:分割式命名的人名、地名不翻译（如MainPoint, mainPoint, main_point, main:point）
- 新增人名、地名后必须以name类型写入术语表。
- 对于不可自然意译的名称（例如 Asta），应采用符合目标语言风格的音译；对于具有明确语义且适合本地化表达的名称（例如 The Guardian），可进行自然意译。
- 音译时不仅要接近原读音，还应考虑名称气质、世界观风格、角色感与文字观感。
- 优先选用：
- 雅观
- 易读
- 具有幻想作品命名感
- 符合目标语言常见译名习惯的用字，而非机械拼音式直译。
- 同一名称在全文中必须保持统一译法。

【规则 7 — 翻译风格】
${prompts.literatureStyle}


【注意】
本次的输入待翻译文字均为${
        when (kind) {
            FormatKind.PlainStr -> "纯文本或Minecraft命令等字面量"
            FormatKind.JsonStr -> "JSON格式"
            FormatKind.SnbtStr, FormatKind.Nbt -> "SNBT格式"
        }
    }

=== 输出协议（必须严格遵守） ===

输出必须严格按照以下格式，不能有任何额外文字：

-- MCT-CLI:TRANSLATED --
[1] <译文第1行>
[2] <译文第2行>
...

注意：<译文> 的内容格式必须与原始输入保持一致。如果某行原文是 JSON 或 SNBT（Minecraft 文本组件），该行译文也必须保持完全相同的 JSON/SNBT 结构，只替换其中的文本值（"text"、"fallback" 等字段）。
-- MCT-CLI:TERMS --
[
{"source": "原文", "target": "译文", "type": "name"},
{"source": "原文2", "target": "译文2", "type": "term"}
]
-- MCT-CLI:END --

关键约束：
- "-- MCT-CLI:TRANSLATED --" 与 "-- MCT-CLI:TERMS --" 以及 "-- MCT-CLI:END --" 必须原样出现。
- TRANSLATED 部分的译文行数 = 输入行数。
- TERMS 部分必须是合法的 JSON 数组，元素包含 source（原文）、target（译文）、type（类型：name 为人名/地名，term 为专有名词）。
- 新发现的术语才放入 TERMS，已存在于术语表中的不要重复。
- 没有新术语时 TERMS 部分写空数组 []。

=== 失败处理 ===
- 如果对某行的结构安全性有疑问，优先保留原文而非冒险修改格式。
- 宁可少翻译一行，也不能破坏数据结构的完整性。
"""

private fun Iterable<Term>.render() = joinToString("\n") { (source, target, _) ->
    "${source.trim()} => ${target.trim()}"
}

typealias RequestTranslation = suspend context(Raise<ChatCompletionCallError>)(
    count: Int, message: String, kind: FormatKind,
    validate: (Pair<TermTable, List<String?>>) -> Boolean
) -> Pair<TermTable, List<String?>>

typealias OnTranslateCancel = (terms: TermTable, salvaged: Map<String, String>) -> Unit

class Translator internal constructor(
    private val call: ChatCompletionCall,
    private val requestTranslation: RequestTranslation,
    defaultTerms: TermTable,
    private val customizedPrompts: CustomizedPrompts = CustomizedPrompts.Default,
    private val tokenThreshold: Int = TOKEN_COUNT_THRESHOLD,
    val concurrency: Int = 1,
) : EnvHolder {
    companion object {
        context(env: Env)
        operator fun invoke(
            call: ChatCompletionCall,
            defaultTerms: TermTable = emptySet(),
            customizedPrompts: CustomizedPrompts = CustomizedPrompts.Default,
            tokenThreshold: Int = TOKEN_COUNT_THRESHOLD,
            concurrency: Int = 1,
        ): Translator {
            return Translator(
                call,
                requestTranslation = { expectedSize, message, kind, validate ->
                    call.chat(
                        prompt = prompt(kind, customizedPrompts),
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

    val terms: MutableSet<Term> = defaultTerms.toMutableSet()

    private val mutex = Mutex()

    context(_: Raise<ChatCompletionCallError>)
    suspend fun translate(
        kind: FormatKind,
        sources: List<String>,
        onCancel: (List<String?>) -> Unit = {}
    ): List<String> = coroutineScope {
        val chunks = sources.withIndex().chunkedByToken(tokenThreshold).toList()
        val totalChunkSize = chunks.size
        logger.info { "Starting translation: ${sources.size} sources → $totalChunkSize chunks, ${terms.size} existing terms, kind: $kind" }
        val translated = MutableList<String?>(sources.size) { null }
        var completedChunks = 0

        suspend fun processChunk(chunkIndex: Int, chunk: List<IndexedValue<String>>) {
            val strips = chunk.stripWithIndex(kind)
            val strippedCount = strips.count { (_, strip) -> strip is CompoundStrip.Success }
            logger.debug { "Chunk $chunkIndex: ${strippedCount}/${strips.size} items stripped to plain text" }
            val termSnapshot = mutex.withLock { terms.toList() }
            val message = buildString {
                if (termSnapshot.isNotEmpty()) {
                    append(termSnapshot.render())
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
                        it.isNotEmpty() && !kind.validate(
                            it
                        )
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

        suspend fun processChunkAndCancellation(
            chunkIndex: Int,
            chunk: MutableList<IndexedValue<String>>
        ) {
            try {
                processChunk(chunkIndex, chunk)
            } catch (e: CancellationException) {
                logger.error { "Translation was cancelled." }
                withContext(NonCancellable) {
                    onCancel(translated)
                }
                throw e
            }
        }

        if (concurrency <= 1) {
            for ((chunkIndex, chunk) in chunks.withIndex()) {
                processChunkAndCancellation(chunkIndex, chunk)
            }
        } else {
            val semaphore = Semaphore(concurrency)
            for ((chunkIndex, chunk) in chunks.withIndex()) {
                launch(Dispatchers.IO) {
                    semaphore.withPermit {
                        processChunkAndCancellation(chunkIndex, chunk)
                    }
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
    data class Success(
        override val original: String,
        val sourceFormat: FormatKind,
        val source: TextCompound,
        val strip: String,
        val isSingleList: Boolean = false,
    ) : CompoundStrip
}

private fun CompoundStrip.stripOrOriginal() = when (this) {
    is CompoundStrip.CannotStrip -> original
    is CompoundStrip.NoCompound -> original
    is CompoundStrip.Success -> strip
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
            FormatKind.JsonStr -> MCTJson.decodeFromString<JsonElement>(raw).let {
                if (it is JsonArray) {
                    it.takeIf { it.size == 1 }?.first()?.also { isList = true }.bind()
                } else it
            }.toIR()

            FormatKind.SnbtStr, FormatKind.Nbt -> Snbt.decodeFromString<NbtTag>(raw).let {
                if (it is NbtList<*>) {
                    it.takeIf { it.size == 1 }?.first()?.also { isList = true }.bind()
                } else it
            }.toIR()

            FormatKind.PlainStr -> null
        }?.decodeToCompound()
    }.getOrNull() ?: return CompoundStrip.NoCompound(raw)

    val strip = (if (compound.extra.isEmpty()) {
        when (compound) {
            is TextCompound.Plain -> compound.text
            else -> cannotStrip()
        }
    } else cannotStrip()) ?: return CompoundStrip.CannotStrip(raw)
    return CompoundStrip.Success(raw, kind, compound, strip, isList)
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
                is CompoundStrip.Success -> {
                    when (cs.sourceFormat) {
                        FormatKind.PlainStr -> s
                        else -> {
                            val ir = cs.source.replaceText(s).encodeToIR().let { e ->
                                if (cs.isSingleList) IRList(e) else e
                            }
                            when (cs.sourceFormat) {
                                FormatKind.JsonStr -> MCTJson.encodeToString(ir.toJsonElement())
                                FormatKind.SnbtStr -> Snbt.encodeToString(ir.toNbtTag())
                                FormatKind.Nbt -> ir.toNbtTag().toSnbt(false)
                            }
                        }
                    }
                }

                is CompoundStrip.CannotStrip -> s
                is CompoundStrip.NoCompound -> s
            }
        } ?: cs.original
        IndexedValue(index, r)
    }

private val LINE_PREFIX = Regex("""^\[(\d+)]\s*""")

internal fun parseLLMResponse(content: String, expectedSize: Int): Pair<TermTable, List<String?>> {
    val (appendedTranslated, appendTermsStr) = REGEX_LLM_OUTPUT.matchEntire(content)?.destructured
        ?: error("LLM responses invalidly: $content")
    val appendTerms = runCatching { Json.decodeFromString<TermTable>(appendTermsStr) }.getOrNull().orEmpty()
    val lines = appendedTranslated.lines()
        .asSequence()
        .mapNotNull { line ->
            val num = LINE_PREFIX.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
            val text = line.replaceFirst(LINE_PREFIX, "")
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
    """(?s)^-- MCT-CLI:TRANSLATED --\n(.*?)\n-- MCT-CLI:TERMS --\n(.*?)(?:\n-- MCT-CLI:END --)?\s*$""".toRegex()


context(_: Raise<ChatCompletionCallError>)
suspend fun Translator.translate(
    groups: List<ExtractionGroup>,
    caches: Map<String, String> = emptyMap(),
    concurrentByKind: Boolean = false,
    onCancel: OnTranslateCancel = { _, _ -> }
): Map<String, String> = coroutineScope {
    if (groups.isEmpty()) {
        logger.debug { "Skipping empty group" }
        return@coroutineScope emptyMap()
    }
    val extractions = groups.flatMap { it.extractions }.groupBy {
        when (it) {
            is DatapackExtraction.MCJson -> FormatKind.JsonStr
            is DatapackExtraction.MCFunction -> FormatKind.PlainStr
            is RegionExtraction -> it.nbt.kind
        }
    }
    val mapping = mutableMapOf<String, String>()
    val mappingMutex = Mutex()

    suspend fun execute(block: suspend (append: suspend (Iterable<Pair<String, String>>) -> Unit) -> Unit) {
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

    extractions.forEach { (kind, extractions) ->
        execute { append ->
            val sources = extractions.asSequence().flatMap {
                it.contents().filter(String::isNotBlank)
            }.distinct().filter { it !in caches }.toList()
            val translated = translate(kind, sources) { translated ->
                val salvaged = buildMap {
                    translated.forEachIndexed { index, translated ->
                        translated?.let {
                            put(sources[index], translated)
                        }
                    }
                }
                onCancel(terms, mapping + salvaged)
            }
            append(sources.zip(translated))
        }
    }
    mapping.toMutableMap().putAll(caches)
    notifier.notify<TranslateSign> { TranslateSign.Progress(1f) }
    env.logger.info { "Built mapping with ${mapping.size} entries" }
    mapping
}

