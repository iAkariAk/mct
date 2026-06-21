package mct.extra.ai.translator

import arrow.core.raise.context.Raise
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import mct.extra.ai.*
import mct.util.IO

typealias OnTermExtractCancel = (TermTable) -> Unit

class TermExtractor(
    val call: ChatCompletionCall,
    val tokenThreshold: Int = TOKEN_COUNT_THRESHOLD,
    val targetLanguage: String = CustomizedPrompts.targetLanguage,
    val concurrency: Int = 1,
    defaultTerms: TermTable = emptySet(),
) {
    private val terms = defaultTerms.toMutableSet()

    context(_: Raise<ChatCompletionCallError>)
    suspend fun extract(
        source: Set<String>,
        onCancel: OnTermExtractCancel,
    ): TermTable {
        val prompt = """
        你是深资的翻译员，你需要提取下列输入中的术语（人名与专有名词）并翻译成$targetLanguage
        
        # 输出格式
        直接输出以下格式的 JSON 数组，不要输出多余的文字：
        [
        {"source": "原文", "target": "译文", "type": "name"},
        {"source": "原文2", "target": "译文2", "type": "term"}
        ]
        
        其中当type=name时代表人名，type=term的时候代表专有名词。
        
        ## 人名、地名处理
        - 不提取翻译 驼峰、下划线、冒号命名：MainPoint、mainPoint、main_point、main:point
        - 不可自然意译的名称（如 Asta）→ 符合目标语言风格的音译
        - 有明确语义且适合本地化（如 The Guardian）→ 可自然意译
        【音译原则】
        - 接近原读音，兼顾名称气质、世界观风格、角色感与文字观感
        - 用字雅观、易读，具有幻想作品命名感
        - 符合目标语言常见译名习惯，而非机械拼音式直译
        - 同一名称在全文中保持统一译法""".trimIndent()


        val chunks = source.chunkedByToken(tokenThreshold)
        coroutineScope {
            chunks.asIterable().forEachConcurrently(
                concurrency,
                Dispatchers.IO,
                { terms.addAll(it) }
            ) { chunk, add ->
                try {
                    val message = chunk.joinToString("\n")
                    val subterms = call.chat(
                        prompt,
                        message,
                        {
                            Json.decodeFromString<TermTable>(it)
                        },
                        validate = { terms -> terms.all { it.source.isNotBlank() && it.target.isNotBlank() } },
                    )
                    add(subterms)
                } catch (e: CancellationException) {
                    try {
                        withContext(NonCancellable) {
                            onCancel(terms)
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

