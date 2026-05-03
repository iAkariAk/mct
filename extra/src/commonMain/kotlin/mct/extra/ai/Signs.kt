package mct.extra.ai

import kotlinx.serialization.Serializable

@Serializable
sealed class AiSign {
    @Serializable
    data class ConsumeToken(val count: Int) : AiSign()
}