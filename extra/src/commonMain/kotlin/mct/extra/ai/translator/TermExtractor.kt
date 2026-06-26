package mct.extra.ai.translator

import arrow.atomic.AtomicBoolean
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
            你是专精 Minecraft 地图的翻译引擎。你需要从输入文本中**仅提取具有命名性质的术语**（人名与专有名词），并翻译成$targetLanguage。
            
            # 输出格式
            直接输出以下格式的 JSON 数组，不要输出多余的文字：
            [
              {"source": "原文", "target": "译文", "type": "name"},
              {"source": "原文2", "target": "译文2", "type": "term"}
            ]
            其中 type=name 代表人物名称，type=term 代表专有名词（如物品名、技能名、地名、状态效果名等）。
            
            ## 核心提取原则
            1. **只提取“命名性”文本块**，即用来称呼、标识某个事物的固定名称，如：
               - 物品名：Sword of Light
               - 技能/效果名：Mars's Madness, Absorption
               - 人名/地名：Asta, The Guardian
            2. **不提取任何功能性、描述性、条件性文本**，即使它们以短语形式存在。判断标准：
               - 如果文本的作用是**说明某物的效果、持续时长、触发条件、属性变化、操作说明**等，一律忽略。
               - 例如：冷却时间说明、伤害数值、范围描述、持有限制、属性加成列表、状态触发方式等，均不提取。
            3. 若输入文本结构为“名称：描述”，只提取冒号前的名称部分（若该名称为有效术语），冒号后的所有内容视为描述并丢弃。
            4. 忽略所有代码式标识符（如 minecraft:command_block）、驼峰/下划线/冒号命名（如 MainPoint, main_point, main:point）以及单独的槽位词（如 MainHand）。但若它们作为复合术语的一部分且已自然语言化（如 “MainHand Power”），则可整体提取。
            
            ## 翻译细则
            - 不可自然意译的名称（如 Asta）→ 采用符合目标语言风格的音译
            - 有明确语义且适合本地化的名称（如 The Guardian）→ 可自然意译
            - 【音译原则】
              - 接近原读音，兼顾名称气质、世界观风格、角色感与文字观感
              - 用字雅观、易读，具有幻想作品命名感
              - 符合目标语言常见译名习惯，而非机械拼音式直译
              - 同一名称在全文中保持统一译法
            
            请严格按照以上规则输出 JSON，不要包含任何解释或额外文本。""".trimIndent()

        val chunks = source.chunkedByToken(tokenThreshold)
        coroutineScope {
            val cancelled = AtomicBoolean(false)
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
                            if (cancelled.compareAndSet(false, true)) {
                                onCancel(terms)
                            }
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

