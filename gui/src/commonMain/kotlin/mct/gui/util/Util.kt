package mct.gui.util

fun Int.renderWithUnit(): String = toLong().renderWithUnit()

fun Long.renderWithUnit(): String = when {
    this <= 1000 -> "$this"
    this < 1_000_000 -> "%.2fk".format(this / 1000.0)
    else -> "%.2fM".format(this / 1_000_000.0)
}

fun ensureJsonExt(path: String): String =
    if (path.endsWith(".json", ignoreCase = true)) path else "$path.json"