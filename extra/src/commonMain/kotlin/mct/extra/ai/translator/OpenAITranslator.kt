package mct.extra.ai.translator

import arrow.core.Option
import arrow.core.raise.Raise
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import mct.Env
import mct.EnvHolder
import mct.FormatKind
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
import mct.util.formatir.IRList
import mct.util.formatir.toIR
import mct.util.formatir.toJson
import mct.util.formatir.toNbt
import mct.util.toSnbt
import net.benwoodworth.knbt.NbtList
import net.benwoodworth.knbt.NbtTag

data class CustomizedPrompts(
    val literatureStyle: String = Defaults.literatureStyle
) {
    companion object Defaults {
        val literatureStyle = """
        - 使用简洁自然的简体中文，轻小说风格。
        - 保持原文的情感色彩和语气。
        - 不要过度意译，忠实于原文含义。
        - 人名、地名使用日文汉字/中文习惯译名。
    """.trimIndent()

        val Default = CustomizedPrompts() // Always at least to wait the above initialization
    }
}

private fun prompt(prompts: CustomizedPrompts) = """你是一名专精 Minecraft 地图汉化的翻译引擎。你的任务是将输入的文本翻译为简体中文，同时严格保护数据结构的完整性。

=== 输入协议 ===
- 输入采用 MCT-CLI 协议格式。
- "-- MCT-CLI:START --" 之前是术语表（JSON 格式），必须严格遵循其中的翻译映射，且术语表本身不要翻译。
- "-- MCT-CLI:START --" 之后是待翻译内容，每行以 "[N] " 开头（N 是行号），后跟该行的文本内容。
- 行内的 "\n" 是转义换行符，不要还原为真实换行。
- 行号 [N] 是该行的唯一标识，你输出的每行译文也必须以相同的 "[N] " 开头。

示例：
```
Kaguya => 辉夜姬
-- MCT-CLI:START --
[1] 待翻译文本行1
[2] 待翻译文本行2
```

=== 核心规则（优先级从高到低） ===

【规则 0 — 行数必须一致（最高优先级）】
- 输出的译文行数必须等于输入行数。
- 禁止合并多行为一行，禁止将一行拆分为多行。
- 顺序必须与输入完全一致。即使相邻行文本相似（如多个药水效果行），也禁止调换顺序——第N行译文必须严格对应第N行原文。
- 如果某行内容无需翻译（纯数字/符号/已为中文），原样保留该行。

【规则 1 — 数据结构保护】
- 内容可能是 JSON 或 SNBT（Minecraft 文本组件）。
- 绝对禁止修改：字段名、键名、对象/数组结构、方括号/花括号/逗号、引号类型。
- 转义序列（如 \n、\"、\\）必须原样保留。
- 输出必须是结构合法的 JSON/SNBT。

【规则 2 — 翻译范围】
- 只翻译字符串值中的自然语言部分。
- 重点翻译 "text" 和 "fallback" 字段的内容。
- 不要翻译：键名、标识符、颜色代码（如 §a、§6）、格式化代码、枚举类值。

【规则 3 — 文本组件保护】
- 对于 Minecraft 的 "translate" + "with" 组件：可以调整 with 数组内元素的顺序以符合中文语序，但不能增减元素数量。
- 对于富文本（text + extra 列表）：可以调整相邻文本节点的顺序以获得自然的中文表达，但样式属性（color、bold、italic 等）必须原封不动保留。
- 禁止跨越语义边界（如 clickEvent、hoverEvent 包裹的文本块）。

【规则 4 — 字符串处理】
- 保持原有引号形式不变（双引号/单引号）。
- 不要修改任何转义字符。
- 如果替换后的文本包含特殊字符，不需要做额外转义（由程序处理）。

【规则 5 — 术语一致性】
- 严格遵循术语表中提供的翻译。
- 同一人物名、地名、物品名在整个翻译中保持统一。
- 不确定时优先一致性而非猜测。

【规则 6 — 翻译风格】
${prompts.literatureStyle}

=== 输出协议（必须严格遵守） ===

输出必须严格按照以下格式，不能有任何额外文字：

-- MCT-CLI:TRANSLATED --
[1] <译文第1行>
[2] <译文第2行>
...
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

private val REGEX_LLM_OUTPUT =
    """(?s)^-- MCT-CLI:TRANSLATED --\n(.*?)\n-- MCT-CLI:TERMS --\n(.*?)(?:\n-- MCT-CLI:END --)?\s*$""".toRegex()

context(_: Env, _: Raise<ChatCompletionCallError>)
private suspend fun ChatCompletionCall.translate(
    customizePrompts: CustomizedPrompts = CustomizedPrompts.Default,
    message: String,
    expectedSize: Int,
): Pair<TermTable, List<String?>> = chat(
    prompt = prompt(customizePrompts),
    message = message,
    parseLLM = {
        parseLLMResponse(it, expectedSize)
    }
)

class OpenAITranslator internal constructor(
    private val call: ChatCompletionCall,
    private val chatCompletion: suspend (Int, String) -> Pair<TermTable, List<String?>>,
    defaultTerms: TermTable,
    private val customizedPrompts: CustomizedPrompts = CustomizedPrompts.Default,
    private val tokenThreshold: Int = TOKEN_COUNT_THRESHOLD
) : Translator {
    companion object {
        context(env: Env, _: Raise<ChatCompletionCallError>)
        operator fun invoke(
            call: ChatCompletionCall,
            defaultTerms: TermTable = emptySet(),
            customizedPrompts: CustomizedPrompts = CustomizedPrompts.Default,
            tokenThreshold: Int = TOKEN_COUNT_THRESHOLD
        ): OpenAITranslator {
            val chatCompletion = suspend { expectedSize: Int, message: String ->
                call.translate(customizedPrompts, message, expectedSize)
            }
            return OpenAITranslator(call, chatCompletion, defaultTerms, customizedPrompts, tokenThreshold)
        }
    }

    override val env get() = call.env

    override val terms: MutableSet<Term> = defaultTerms.toMutableSet()

    private val mutex = Mutex()

    override suspend fun translate(kind: FormatKind, sources: List<String>): List<String> {
        val chunks = sources.chunkedByToken(tokenThreshold).toList()
        val totalChunkSize = chunks.size
        logger.info { "Starting translation: ${sources.size} sources → $totalChunkSize chunks, ${terms.size} existing terms" }
        val translated = chunks.withIndex().fold(mutableListOf<String>()) { translated, (index, chunk) ->
            val strips = chunk.strip(kind)
            val strippedCount = strips.count { it is CompoundStrip.Success }
            logger.debug { "Chunk $index: ${strippedCount}/${strips.size} items stripped to plain text" }
            val message = buildString {
                if (terms.isNotEmpty()) {
                    append(terms.render())
                    appendLine()
                }
                appendLine("-- MCT-CLI:START --")
                strips.map { strip ->
                    val str = strip.stripOrOriginal()
                    str.replace("\n", "\\n")
                }.forEachIndexed { i, text ->
                    appendLine("[${i}] $text")
                }
            }
            logger.info { "Handling ${index + 1} (total $totalChunkSize)" }

            val (appendTerms, appendedTranslatedRaw) = chatCompletion(strips.size, message)
            val appendedTranslated = strips.destrip(appendedTranslatedRaw)
            translated.addAll(appendedTranslated)
            logger.info { "Handled ${index + 1} (total $totalChunkSize)" }
            logger.debug {
                chunk.zip(appendedTranslated).joinToString("\n") { (x, y) -> "Translate $x => $y" }
            }
            mutex.withLock {
                terms += appendTerms
            }
            val pct = (index + 1).toFloat() / totalChunkSize
            logger.sign<TranslateSign> { TranslateSign.Progress(pct) }

            translated
        }
        logger.info { "Translation complete: ${translated.size} items, ${terms.size} terms accumulated" }
        return translated
    }

    override fun toString() = "OpenAITranslator($call, $customizedPrompts)"
}

internal sealed interface CompoundStrip {
    val original: String

    data class Failure(override val original: String) : CompoundStrip
    data class Success(
        override val original: String,
        val sourceFormat: FormatKind,
        val source: TextCompound,
        val strip: String,
        val isSingleList: Boolean = false,
    ) : CompoundStrip
}

private fun CompoundStrip.stripOrOriginal() = when (this) {
    is CompoundStrip.Failure -> original
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
            FormatKind.Json -> MCTJson.decodeFromString<JsonElement>(raw).let {
                if (it is JsonArray) {
                    it.takeIf { it.size == 1 }?.first()?.also { isList = true }.bind()
                } else it
            }.toIR()

            FormatKind.Snbt -> Snbt.decodeFromString<NbtTag>(raw).let {
                if (it is NbtList<*>) {
                    it.takeIf { it.size == 1 }?.first()?.also { isList = true }.bind()
                } else it
            }.toIR()
        }.decodeToCompound()
    }.getOrNull() ?: return CompoundStrip.Failure(raw)

    val strip = (if (compound.extra.isEmpty()) {
        when (compound) {
            is TextCompound.Plain -> compound.text
            else -> cannotStrip()
        }
    } else cannotStrip()) ?: return CompoundStrip.Failure(raw)
    return CompoundStrip.Success(raw, kind, compound, strip, isList)
}

context(env: EnvHolder)
internal fun List<String>.strip(kind: FormatKind): List<CompoundStrip> = map { it.strip(kind) }

internal fun List<CompoundStrip>.destrip(response: List<String?>): List<String> =
    zip(response).map { (cs, s) ->
        s?.let {
            when (cs) {
                is CompoundStrip.Success -> {
                    val ir = cs.source.replaceText(s).encodeToIR().let { e ->
                        if (cs.isSingleList) IRList(e) else e
                    }
                    when (cs.sourceFormat) {
                        FormatKind.Json -> MCTJson.encodeToString<JsonElement>(ir.toJson())
                        FormatKind.Snbt -> ir.toNbt().toSnbt(false)
                    }
                }

                is CompoundStrip.Failure -> s
            }
        } ?: cs.original
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
                .replace("\\n", "\n")
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


