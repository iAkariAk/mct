package mct.cli.translator

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import korlibs.math.toIntFloor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mct.Env
import mct.Logger

private const val PROMPT = """你是一名专精 Minecraft 地图汉化的翻译引擎。你的任务是将输入的文本翻译为简体中文，同时严格保护数据结构的完整性。

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
- 使用简洁自然的简体中文，轻小说风格。
- 保持原文的情感色彩和语气。
- 不要过度意译，忠实于原文含义。
- 人名、地名使用日文汉字/中文习惯译名。

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

private const val TOKEN_COUNT_THRESHOLD = 2 shl 10

private fun Iterable<Term>.render() = joinToString("\n") { (source, target, _) ->
    "${source.trim()} => ${target.trim()}"
}

private val REGEX_LLM_OUTPUT =
    """(?s)^-- MCT-CLI:TRANSLATED --\n(.*?)\n-- MCT-CLI:TERMS --\n(.*?)(?:\n-- MCT-CLI:END --)?\s*$""".toRegex()

// ── custom serializable types for direct API calls ───────────

@Serializable
private data class ApiMessage(val role: String, val content: String)

@Serializable
private data class ApiRequest(
    val model: String,
    val messages: List<ApiMessage>,
)

@Serializable
private data class ApiResponse(
    val choices: List<Choice>? = null,
) {
    @Serializable
    data class Choice(val message: ApiMessage? = null)
}

// ── translator ───────────────────────────────────────────────

class OpenAITranslator(
    private val apiUrl: String?,
    private val token: String,
    private val model: String,
    private val defaultTerms: TermTable,
    private val env: Env = Env.Default
) : Translator {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
    }

    private val client = HttpClient(CIO) {
        engine { dispatcher = Dispatchers.IO }
        install(HttpTimeout) {
            requestTimeoutMillis = 300_000 // 5 minutes for translation
            connectTimeoutMillis = 30_000
        }
        install(ContentNegotiation) {
            json(json)
        }
        install(Logging) {
            logger = object : io.ktor.client.plugins.logging.Logger {
                override fun log(message: String) {
                    env.logger.debug { message }
                }
            }
            level = LogLevel.ALL
        }
    }

    override val terms: MutableSet<Term> = defaultTerms.toMutableSet()

    private val mutex = Mutex()

    private suspend fun chatCompletion(message: String): String {
        val url = (apiUrl ?: "https://api.openai.com/v1") + "/chat/completions"
        val request = ApiRequest(
            model = model,
            messages = listOf(
                ApiMessage(role = "system", content = PROMPT),
                ApiMessage(role = "user", content = message),
            )
        )
        var lastError: Exception? = null
        repeat(3) { attempt ->
            try {
                val response = client.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                    headers["Authorization"] = "Bearer $token"
                }
                val body = response.bodyAsText()
                val apiResponse = json.decodeFromString<ApiResponse>(body)
                val content = apiResponse.choices?.firstOrNull()?.message?.content
                    ?: error("No content in response: $body")
                return content
            } catch (e: Exception) {
                lastError = e
                if (attempt < 2) {
                    kotlinx.coroutines.delay(2000L * (attempt + 1))
                    env.logger.warning { "请求失败，第${attempt + 1}次重试: ${e.message}" }
                }
            }
        }
        throw lastError ?: error("Unknown error")
    }

    override suspend fun translate(sources: List<String>): List<String> {
        var tokenCount = 0
        val chunk = sequence {
            val tmp = mutableListOf<String>()
            sources.forEach { source ->
                val approximateTokenCount = calculateToken(source.length + 1)
                val isThresholdOver = tokenCount + approximateTokenCount >= TOKEN_COUNT_THRESHOLD
                if (isThresholdOver) {
                    require(tmp.isNotEmpty()) { "The content size too large." }
                    yield(tmp)
                    tokenCount = 0
                    tmp.clear()
                }
                tmp += source
                tokenCount += approximateTokenCount
            }
            if (tmp.isNotEmpty()) {
                yield(tmp)
            }
        }.withIndex().toList()
        val chunkCount = chunk.size
        val translated = chunk.fold(mutableListOf<String>()) { translated, (index, chunk) ->
            val message = buildString {
                if (terms.isNotEmpty()) {
                    append(terms.render())
                    appendLine()
                }
                appendLine("-- MCT-CLI:START --")
                chunk.map { it.replace("\n", "\\n") }.forEachIndexed { i, text ->
                    appendLine("[${i + 1}] $text")
                }
            }
            env.logger.info { "Handling $index (total ${chunkCount - 1})" }

            var llmRetry = 0
            lateinit var appendTerms: Set<Term>
            lateinit var appendedTranslated: List<String>
            while (llmRetry < 3) {
                val completion = chatCompletion(message)
                val (t, tr) = parseLLMResponse(completion)
                if (tr.size == chunk.size) {
                    appendTerms = t
                    appendedTranslated = tr
                    break
                }
                llmRetry++
                env.logger.warning { "翻译行数不匹配: 期望 ${chunk.size}, 实际 ${tr.size}, 重试 $llmRetry/3" }
                if (llmRetry >= 3) {
                    error("LLM 返回行数与输入不匹配 (期望 ${chunk.size}, 实际 ${tr.size}), 重试耗尽")
                }
            }

            terms += appendTerms
            env.logger.info { "Handled $index (total ${chunkCount - 1})" }
            env.logger.debug { chunk.zip(appendedTranslated).joinToString("\n") { (x, y) -> "Translate $x => $y" } }
            mutex.withLock {
                translated += appendedTranslated
            }
            translated
        }
        return translated
    }

    override fun toString() = "OpenAITranslator($model)"
}

private val LINE_PREFIX = Regex("""^\[(\d+)\]\s*""")

internal fun parseLLMResponse(content: String): Pair<TermTable, List<String>> {
    val (appendedTranslated, appendTermsStr) = REGEX_LLM_OUTPUT.matchEntire(content)?.destructured
        ?: error("LLM responses invalidly")
    val appendTerms = Json.decodeFromString<TermTable>(appendTermsStr)
    // 按 [N] 行号重新排序，即使 LLM 输出乱序也能正确配对
    val lines = appendedTranslated.lines()
        .map { line ->
            val num = LINE_PREFIX.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val text = line.replaceFirst(LINE_PREFIX, "")
            num to text
        }
        .sortedBy { it.first }
        .map { it.second }
    return appendTerms to lines
}

private fun calculateToken(strSize: Int): Int = (strSize / 1.5).toIntFloor()
