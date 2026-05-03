package mct.extra.ai.translator

import kotlinx.serialization.Serializable

@Serializable
sealed interface TranslateSign {
    @Serializable
    data class Progress(val progress: Float) : TranslateSign
}