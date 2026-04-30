package mct.util.translator

sealed interface TranslateSign {
    data class Begin(val batch: Int) : TranslateSign
    data class Progress(val progress: Float) : TranslateSign
}