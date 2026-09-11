package mct.gui.util

fun Int.renderWithUnit(): String = toLong().renderWithUnit()

fun Long.renderWithUnit(): String = when {
    this <= 1000 -> "$this"
    this < 1_000_000 -> "%.2fk".format(this / 1000.0)
    else -> "%.2fM".format(this / 1_000_000.0)
}

fun ensureExtension(path: String, extension: String): String =
    if (path.endsWith(".$extension", ignoreCase = true)) path else "$path.$extension"

fun ensureJsonExt(path: String): String = ensureExtension(path, "json")