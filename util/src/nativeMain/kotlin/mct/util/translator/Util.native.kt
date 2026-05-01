package mct.util.translator

import korlibs.math.toIntFloor

internal actual fun calculateToken(str: String): Int = (str.length / 1.5).toIntFloor() // TODO: migrate to a more efficient method
