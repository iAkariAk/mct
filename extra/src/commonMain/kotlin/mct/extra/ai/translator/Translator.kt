package mct.extra.ai.translator

import arrow.atomic.AtomicBoolean
import arrow.core.Option
import arrow.core.raise.Raise
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import mct.EnvHolder
import mct.extra.ai.*
import mct.model.patch.*
import mct.notify
import mct.serializer.MCTJson
import mct.serializer.Snbt
import mct.text.TextCompound
import mct.text.decodeToCompound
import mct.text.encodeToIR
import mct.text.replaceText
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

## 输入格式

- 输入采用 **MCT-CLI** 协议格式。
- "-- MCT-CLI:START --" 之前是术语表（JSON 格式），必须严格遵循其中的翻译映射；术语表本身不要翻译。
- "-- MCT-CLI:START --" 之后是待翻译内容。每行以 **[N] ** 开头（N 是行号，从 0 开始），后跟该行的文本内容。
- 行内的 **\n** 和 **↠mctnl↠** 是转义换行符——不要还原为真实换行，不要删去，也不要在其中插入字词。
- 行号 [N] 是该行的唯一标识。**你的每行译文输出也必须以相同的 [N] 开头。**

示例（请始终翻译成${prompts.targetLanguage}）：
Kaguya => 辉夜姬
-- MCT-CLI:START --
[1] 待翻译文本行1
[2] 待翻译文本行2

## 核心规则（优先级从高到低）

### 规则 0 — 行数完全一致（最高优先级）
【必须】
- 输出译文行数 **必须等于** 输入行数
- 第 N 行译文必须严格对应第 N 行原文，顺序完全一致
- 某行无需翻译时（纯数字、符号、已为中文），原样保留该行
【禁止】
- 禁止合并多行为一行
- 禁止将一行拆分为多行
- 禁止调换行顺序（即使相邻行文本相似也不允许）

### 规则 1 — 数据结构保护
内容可能是 JSON、SNBT（Minecraft 文本组件）或完整的 Minecraft 命令。
【必须】
- 保留所有结构：字段名、键名、对象/数组、方括号/花括号/逗号、引号类型
- 保留所有转义序列（如 \n、\"、\\）
- 输出必须是结构合法的 JSON/SNBT
- 不翻译命令关键字等非文本部分（如命令中的 spawner）${
        if (prompts.handleGradientAggressively) """
- 唯一例外：启用「渐变色激进策略」时，允许对渐变文本组件的颜色数组进行插值调整（见规则 3 补充条款），其他结构保护不变""" else ""
    }
- 生成最终结果前，必须执行一次静态检查。检查内容：
  1. 所有 { 与 } 数量一致
  2. 所有 [ 与 ] 数量一致
  3. 所有字符串引号成对出现
  4. 所有 key:value 中冒号存在
  5. 不存在 {"":minecraft:xxx} 这类缺失字符串引号的情况
  6. 不存在 {"":58.0"} 这类数字与字符串混合的情况
  7. 输出结构与输入结构保持一致
 若发现问题，修复后再输出最终结果。

### 规则 2 — 翻译范围
只翻译字符串值中的 **自然语言** 部分。
【必须】
- 重点翻译 "text" 和 "fallback" 字段的内容
【禁止】
- 禁止翻译：键名、标识符、颜色代码（§a、§6）、格式化代码、枚举类值、Minecraft 命令逻辑部分

### 规则 3 — 文本组件保护
【允许】
- "translate" + "with" 组件：可调整 with 数组内元素的顺序以符合中文语序，但 **不能增减元素数量**
- "text" + "extra" 富文本：可调整相邻文本节点的顺序以获得自然的中文表达，但 **样式属性（color、bold、italic 等）必须原封不动保留**${
        if (prompts.handleGradientAggressively) """
- 【渐变色文本组件激进处理】（仅当此条款激活时生效）
  渐变色组件由多个带 color 属性的 text 节点组成。处理流程：
  (1) 先正常翻译各节点内的文本，尽量保留原始字符数
  (2) 长度适配（仅在译文总长度与原文差距过大、可能导致渐变断裂时启用）：
      - 译文过短 → 允许语义扩写（忽略节点限制，增加修饰词/重复语素/补充意境词）
        若扩写后仍显著偏短：可删除部分中间颜色节点（从 extra 数组中移除过渡色片段），简化渐变
        禁止直接保留原文不译
      - 译文过长 → 允许插入额外颜色插值节点，将译文拆分为更多片段并补充中间色
        新增节点的颜色从相邻节点插值计算（如 #FF0000 ←→ #0000FF 之间插入 #7F007F）
  (3) 长度参考：中文 1 字 ≈ 英文 2 字符，以视觉等宽而非字符数相等评估
  (4) 调整后渐变效果不如原始 → 只要数据结构合法且译文准确，即视为成功
  示例：
  "Legendary Frost Guardian"（23 字符）→ "永冬寒霜的传奇守护者"（11 字≈视觉 22 字符）—— 合格
  "Frost Guardian" → 先扩写为"永霜守护者"，仍不足时删除部分颜色节点"""
        else ""
    }
【禁止】
- 禁止跨越语义边界（如 clickEvent、hoverEvent 包裹的文本块）
- 禁止跨行调整语序（**行号必须完全对齐**）
 - 如:
   [0] BRING MIKE
   [1] IF YOU GO TO
   [2] THE SEWERS
   不应该合并，而是逐行翻译:
   [0] 带上牛奶
   [1] 如果你去
   [2] 下水道
- 禁止在翻译输出中添加任何额外内容（空行、注释等）

### 规则 4 — 字符串处理
【必须】
- 保持原有引号形式不变（双引号 / 单引号）
- 保留所有转义字符
【注意】
- 替换后的文本若含特殊字符不需要额外转义（由程序处理）

### 规则 5 — 术语一致性
- 严格遵循术语表中提供的翻译映射
- 同一人物名、地名、物品名在全文中保持统一
- 不确定时优先一致性而非猜测

### 规则 6 — 人名、地名处理
【不翻译】
- 驼峰、下划线、冒号命名：MainPoint、mainPoint、main_point、main:point
【必须】
- 新增人名/地名后以 name 类型写入术语表
- 不可自然意译的名称（如 Asta）→ 符合目标语言风格的音译
- 有明确语义且适合本地化（如 The Guardian）→ 可自然意译
【音译原则】
- 接近原读音，兼顾名称气质、世界观风格、角色感与文字观感
- 用字雅观、易读，具有幻想作品命名感
- 符合目标语言常见译名习惯，而非机械拼音式直译
- 同一名称在全文中保持统一译法

### 规则 7 — 翻译风格
${prompts.literatureStyle}

## 输出格式（必须严格遵守）

输出必须严格按照以下结构，**不能有任何额外文字**：

-- MCT-CLI:TRANSLATED --
[1] <译文第1行>
[2] <译文第2行>
...

> 译文内容的格式必须与原始输入保持一致。原文是 JSON → 译文也必须是相同结构的 JSON，只替换 "text"、"fallback" 等字段的值。

-- MCT-CLI:TERMS --
[
  {"source": "原文", "target": "译文", "type": "name"},
  {"source": "原文2", "target": "译文2", "type": "term"}
]
-- MCT-CLI:END --

关键约束：
- "-- MCT-CLI:TRANSLATED --"、"-- MCT-CLI:TERMS --"、"-- MCT-CLI:END --" 三个标记必须逐字原样出现
- TRANSLATED 部分的行数 **必须等于** 输入行数
- TERMS 必须是合法 JSON 数组，元素包含 source、target、type（name=人名/地名，term=专有名词）
- **仅新发现的术语**放入 TERMS，已存在于术语表中的不要重复
- 没有新术语时 TERMS 写空数组 []

## 失败处理
- 对某行的结构安全性有疑问时 → 优先保留原文
- 宁可少翻译一行，也不能破坏数据结构的完整性

## 本次输入类型
${
        when (kind) {
            FormatKind.PlainStr -> "纯文本或 Minecraft 命令等字面量"
            FormatKind.JsonStr -> "JSON 格式"
            FormatKind.SnbtStr, FormatKind.Nbt -> "SNBT 格式"
        }
    }

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

        chunks.withIndex().forEachConcurrently<IndexedValue<MutableList<IndexedValue<String>>>, Unit>(
            concurrency,
            Dispatchers.IO,
            { _ -> },
        ) { (chunkIndex, chunk), _ ->
            try {
                processChunk(chunkIndex, chunk)
            } catch (e: CancellationException) {
                logger.error { "Translation was cancelled." }
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
            is DatapackExtraction.Nbt -> it.nbt.kind
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
    val cancelled = AtomicBoolean(false)
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

