package mct.extra.ai.translator

import arrow.core.raise.context.Raise
import mct.extra.ai.ChatCompletionCall
import mct.extra.ai.ChatCompletionCallError
import mct.extra.ai.chatRaw

private val PROMPT =
    "你是一名精通中文修辞的提示词优化专家。请优化以下 Minecraft 翻译助手的翻译风格提示词，使其更加优美、自然、富有文采，符合中文表达习惯。保持原有含义和规则不变，只提升语言的美感和表现力。直接返回优化后的文本，不要解释说明。"

context(_: Raise<ChatCompletionCallError>)
suspend fun ChatCompletionCall.optimizePrompt(currentPrompt: String): String {
    return chatRaw(PROMPT, currentPrompt).trim()
}
