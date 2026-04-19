package mct.cli.translator

import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.logging.LogLevel
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.LoggingConfig
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIHost
import io.ktor.client.plugins.logging.*
import korlibs.math.toIntFloor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import mct.Env

private const val PROMPT = """You are a professional translator specialized in structured data.

Your task is to translate human-readable text into Chinese, while strictly preserving the original data structure.

=== INPUT PROTOCOL ===
- The input follows the MCT-CLI protocol.
- Everything before "-- MCT-CLI:START --" is a terminology table in JSON format.
- Everything after "-- MCT-CLI:START --" is the content to translate.
- The terminology table must NOT be translated.

e.g. 
```
Kuguya => 辉夜姬
-- MCT-CLI:START --
<some text needing translating>
```

=== INPUT FORMAT ===
- The content consists of multiple lines.
- Each line is an independent string.
- Line breaks inside a line are escaped as "\n" and must NOT be unescaped.

=== STRICT RULES ===

1. STRUCTURE PRESERVATION (HIGHEST PRIORITY)
- The content may be JSON or SNBT (Minecraft TextCompound).
- You MUST NOT modify:
  - keys, field names
  - object/array structure (except allowed cases below)
  - brackets, commas, or syntax
  - escape sequences (e.g. "\n")
- Output must remain structurally valid.

2. CONTROLLED STRUCTURAL ADJUSTMENT

2.1 TRANSLATE COMPONENT
- For "translate" with "with":
  - You MAY reorder elements in "with" for natural Chinese word order.
- You MUST NOT:
  - change element count
  - remove/duplicate elements
  - modify non-text fields
  - move elements outside "with"
  - change the "translate" key

2.2 RICH TEXT (TEXT + EXTRA)
- You MAY reorder or merge adjacent text nodes for natural Chinese.
- ONLY IF:
  - all styles (color, bold, etc.) are preserved exactly
  - final rendering is equivalent except word order
- You MUST NOT:
  - cross semantic boundaries (clickEvent, hoverEvent, etc.)
  - alter non-text properties
  - break style scopes

3. TRANSLATION SCOPE
- ONLY translate natural language inside string values.
- Focus on "text" and "fallback".
- DO NOT translate:
  - keys, identifiers
  - enum-like values
  - formatting codes or color names

4. STRING HANDLING
- Preserve quoting exactly.
- Do NOT change escape sequences.

5. MULTILINE CONSISTENCY
- Output line count MUST equal input.
- Keep exact order.
- Do NOT merge or split lines.

6. TERMINOLOGY & CONSISTENCY
- Follow provided terminology strictly.
- Keep terms consistent.
- Prefer consistency over guessing.

7. STYLE (LOW PRIORITY)
- Use a light, natural, slightly lively tone (similar to light novel style).
- Keep style subtle.
- Do NOT alter meaning or structure.

=== OUTPUT PROTOCOL (MANDATORY) ===

You MUST output exactly:

-- MCT-CLI:TRANSLATED --
<one translated line per input line>
-- MCT-CLI:TERMS --
[
  {
    "source": "<original>",
    "target": "<chinese>",
    "type": "name | term"
  }
]
-- MCT-CLI:END --

=== OUTPUT RULES ===
- No extra text anywhere.
- Section headers must match exactly.
- Order must not change.

TRANSLATED:
- Same number of lines as input
- Same order
- No merge/split

TERMS:
- Must be valid JSON
- Only newly discovered terms
- No duplicates
- If none:

[]

=== FAILURE RULE ===
- If unsure, prioritize valid format over fluency.

=== GOAL ===
Produce a natural Chinese translation while preserving structure and style, allowing only safe reordering in "translate.with" and rich text trees.
"""

private const val TOKEN_COUNT_THRESHOLD = 50 shl 10

private fun Iterable<Term>.render() = joinToString("\n") { (source, target, _) ->
    "${source.trim()} => ${target.trim()}"
}

private val REGEX_LLM_OUTPUT =
    """(?s)^-- MCT-CLI:TRANSLATED --\n(.*?)\n-- MCT-CLI:TERMS --\n(.*?)\n-- MCT-CLI:END --\s*$""".toRegex()

class OpenAITranslator(
    private val apiUrl: String?,
    private val token: String,
    private val model: String,
    private val defaultTerms: TermTable,
    private val env: Env = Env.Default
) : Translator {
    private val client = OpenAI(
        token,
        host = apiUrl?.let(::OpenAIHost) ?: OpenAIHost.OpenAI,
        logging = LoggingConfig(
            logLevel = LogLevel.All,
        ), httpClientConfig = {
            engine {
                dispatcher = Dispatchers.IO // https://github.com/aallam/openai-kotlin/issues/461
            }
            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        env.logger.debug { message }
                    }
                }
            }
        }
    )

    override val terms: MutableSet<Term> = defaultTerms.toMutableSet()

    private val mutex = Mutex()

    private suspend fun chatCompletion(message: String) = client.chatCompletion(
        ChatCompletionRequest(
            model = ModelId(model),
            messages = listOf(
                ChatMessage(
                    role = ChatRole.System,
                    content = PROMPT,
                ),
                ChatMessage(
                    role = ChatRole.User,
                    content = message,
                )
            )
        ),
    )

    override suspend fun translate(sources: List<String>): List<String> {
        var tokenCount = 0
        val chunk = sequence {
            val tmp = mutableListOf<String>()
            sources.forEach { source ->
                val approximateTokenCount = calculateToken(source.length + 1)
                val isThresholdOver = tokenCount + approximateTokenCount >= TOKEN_COUNT_THRESHOLD
                if (isThresholdOver) {
                    require(tmp.isNotEmpty()) {
                        "The content size too large."
                    }
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

                append("-- MCT-CLI:START --")
                chunk.map { it.replace("\n", "\\n") }.forEach { appendLine(it) }
            }
            env.logger.info { "Handling $index (total ${chunkCount - 1})" }

            val completion = chatCompletion(message)

            val (appendTerms, appendedTranslated) = parseLLMResponse(completion.choices.first().message.content!!)
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

internal fun parseLLMResponse(content: String): Pair<TermTable, List<String>> {
    val (appendedTranslated, appendTermsStr) = REGEX_LLM_OUTPUT.matchEntire(content)?.destructured
        ?: error("LLM responses invalidly")
    val appendTerms = Json.decodeFromString<TermTable>(appendTermsStr)
    return appendTerms to appendedTranslated.lines()
}

private fun calculateToken(strSize: Int): Int = (strSize / 1.5).toIntFloor()