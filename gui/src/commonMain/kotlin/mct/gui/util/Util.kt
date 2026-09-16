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

/** "刚刚" / "N 分钟前" / "N 小时前" / "N 天前" / date, for the project history entries. */
fun formatElapsed(sinceEpochMillis: Long, now: Long = System.currentTimeMillis()): String {
    val seconds = ((now - sinceEpochMillis) / 1000).coerceAtLeast(0)
    return when {
        sinceEpochMillis <= 0 -> "从未打开"
        seconds < 60 -> "刚刚"
        seconds < 3_600 -> "${seconds / 60} 分钟前"
        seconds < 86_400 -> "${seconds / 3_600} 小时前"
        seconds < 2_592_000 -> "${seconds / 86_400} 天前"
        else -> java.time.Instant.ofEpochMilli(sinceEpochMillis)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
            .toString()
    }
}