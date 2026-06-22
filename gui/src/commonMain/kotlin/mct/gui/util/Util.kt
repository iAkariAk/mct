package mct.gui.util

private val Int.k get() = this / 1000.0
private val Int.m get() = this / 1_000_000.0

fun Int.renderWithUnit(): String = when {
    this <= 1000 -> "$this"
    this < 1_000_000 -> "%.2fk".format(this / 1000.0)
    else -> "%.2fM".format(this / 1_000_000.0)
}

fun ensureJsonExt(path: String): String =
    if (path.endsWith(".json", ignoreCase = true)) path else "$path.json"