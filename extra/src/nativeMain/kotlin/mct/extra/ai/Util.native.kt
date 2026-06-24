package mct.extra.ai

internal actual fun calculateToken(str: String): Int = (str.length / 1.5).toInt() // TODO: migrate to a more efficient method
