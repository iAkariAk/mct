package mct.extra.ai.translator

import arrow.core.raise.context.Raise
import mct.extra.ai.ChatCompletionCall
import mct.extra.ai.ChatCompletionCallError
import mct.extra.ai.chatRaw

private const val PROMPT_OPTIMIZER = """
你是一名专业 Prompt Engineer。请对以下提示词进行重构和优化，使其更适合LLM执行。
要求:
- 保持原始意图、规则和输出要求完全一致；
- 不添加新的业务逻辑或额外要求；
- 消除歧义和隐含假设；
- 将模糊描述改写为明确、可验证、可执行的指令；
- 优化信息组织结构，将相关内容归类；
- 减少模型产生误解或遗漏约束的可能性；
- 优先考虑执行效果，而非语言修饰。

输出优化后的完整提示词，不要分析过程，不要解释修改原因。
"""

context(_: Raise<ChatCompletionCallError>)
suspend fun ChatCompletionCall.optimizePrompt(currentPrompt: String): String {
    return chatRaw(PROMPT_OPTIMIZER, currentPrompt).trim()
}
