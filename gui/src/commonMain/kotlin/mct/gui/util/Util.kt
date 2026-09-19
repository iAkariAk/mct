package mct.gui.util

import okio.FileSystem
import okio.Path

fun Int.renderWithUnit(): String = toLong().renderWithUnit()

/**
 * Write a file through a sibling `<name>.tmp` that is moved onto the target once [write] returns.
 *
 * Every file this app replaces is one the user can lose: `mct.toml`, a translation table, an
 * extraction result, a patch, the settings. Writing in place truncates the target before a single
 * byte is produced, so a failed serialization, a full disk or a power loss leaves it half-written;
 * going through a temp file keeps the previous contents intact until the new ones are complete.
 *
 * The parent directory is created for the same reason: these destinations are ones the user just
 * picked, and the panels deliberately accept paths whose directory does not exist yet.
 *
 * A temp file left behind by an earlier crash is removed first, because the writers this helper is
 * handed (okio's `write`, `Path.writeText`, `Path.writeJson`) create their target exclusively — a
 * stale `.tmp` would make every later write of that file fail instead of being replaced.
 */
fun writeAtomically(fs: FileSystem, target: Path, write: (Path) -> Unit) {
    val parent = target.parent
    if (parent != null) fs.createDirectories(parent)
    val temp = (parent ?: target) / (target.name + ".tmp")
    runCatching { fs.delete(temp) }
    write(temp)
    fs.atomicMove(temp, target)
}

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