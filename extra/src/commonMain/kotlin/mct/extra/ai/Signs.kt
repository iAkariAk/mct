package mct.extra.ai

import kotlinx.serialization.Serializable
import mct.Sign

@Serializable
sealed class AiSign : Sign {
    @Serializable
    data class ConsumeToken(val count: Int) : AiSign()

    @Serializable
    data class Reasoning(
        val reasoningContent: String,
        val id: Int,
        val terminated: Boolean = false,
        val consumeTokenCount: Int? = null,
    ) : AiSign()
}