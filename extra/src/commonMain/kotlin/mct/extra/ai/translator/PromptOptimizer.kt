package mct.extra.ai.translator

import arrow.core.raise.context.Raise
import mct.extra.ai.ChatCompletionCall
import mct.extra.ai.ChatCompletionCallError
import mct.extra.ai.chatRaw

internal const val PROMPT_OPTIMIZER = """
你是一名专业的翻译风格提示词编辑器。输入内容只是 Minecraft 地图翻译系统中“译文/译名风格”章节的一段规则，不是完整系统提示词。

请在不改变原意的前提下重写这段风格规则，使其简洁、明确且便于 LLM 执行：

- 保留用户指定的文体、语气、忠实程度、命名倾向和目标读者。
- 合并重复要求，消除相互冲突、模糊或无法执行的表述。
- 使用短小、相互独立的 Markdown 无序列表项。
- 不添加用户未要求的创作风格或业务规则。
- 不添加角色定义、目标语言、输入输出协议、JSON/SNBT/Minecraft 结构保护、术语提取或解释性文字；这些由外层系统提示负责。
- 不使用标题、代码围栏、前言或结语。

只输出优化后的风格规则列表。
"""

context(_: Raise<ChatCompletionCallError>)
suspend fun ChatCompletionCall.optimizePrompt(currentPrompt: String): String {
    return chatRaw(PROMPT_OPTIMIZER, currentPrompt).trim()
}
