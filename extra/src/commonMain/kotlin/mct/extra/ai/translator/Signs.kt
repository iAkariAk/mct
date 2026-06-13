package mct.extra.ai.translator

import kotlinx.serialization.Serializable
import mct.Sign

@Serializable
sealed interface TranslateSign : Sign {
    @Serializable
    data class Progress(val progress: Float) : TranslateSign
}