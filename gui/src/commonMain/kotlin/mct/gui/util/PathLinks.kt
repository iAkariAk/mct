package mct.gui.util

import java.awt.Desktop
import java.io.File

/** A filesystem path mentioned by a console line, resolved to an absolute path that exists. */
data class PathLink(
    /** Range of the path inside the console message. */
    val range: IntRange,
    /** Absolute path, as handed to the file manager. */
    val path: String,
)

/**
 * A path token: everything up to the next whitespace or quote. It may not start with `:` (that is
 * punctuation such as `已写入:`), and the character set is deliberately not restricted to ASCII —
 * the usual path in this app contains Chinese (`D:\我的世界\…`), and a truncated match such as
 * `E:\` would link to the drive root instead of the file.
 */
private val PathCandidate = Regex("""[^\s"'<>|:][^\s"'<>|]*""")
private val DrivePrefix = Regex("""^[A-Za-z]:""")
private val FileExtension = Regex("""\.[A-Za-z0-9]{1,6}$""")

/**
 * How many following words a token may grow into. Paths may contain spaces, so a token that does
 * not resolve is retried with the next word appended; the cap keeps that from scanning prose.
 */
private const val MAX_EXTRA_WORDS = 3

/**
 * Paths mentioned in [message] that exist on disk, in left-to-right order.
 *
 * A token only counts as a path when it has a separator, a drive prefix or a file extension *and*
 * resolves to an existing file, so prose that happens to contain dots stays plain text and a
 * missing path is not turned into a link that would do nothing. Relative paths resolve against
 * [workingDirectory], the directory the application runs in.
 */
fun findPathLinks(
    message: String,
    workingDirectory: File = File(System.getProperty("user.dir")),
): List<PathLink> {
    val links = ArrayList<PathLink>(2)
    var searchFrom = 0
    while (true) {
        val match = PathCandidate.find(message, searchFrom) ?: return links
        searchFrom = match.range.last + 1

        for (end in candidateEnds(message, match.range.last + 1)) {
            val token = message.substring(match.range.first, end)
                .trimEnd('.', ',', ';', ':', '，', '。', '、', '；', '：', ')', '）', ']', '】', '」')
            if (!looksLikePath(token)) break
            val file = File(token).let { if (it.isAbsolute) it else File(workingDirectory, token) }
            if (file.exists()) {
                links += PathLink(match.range.first until (match.range.first + token.length), file.path)
                searchFrom = end
                break
            }
        }
    }
}

/** Ends of the [start]-anchored candidates: the token itself, then each following word. */
private fun candidateEnds(message: String, firstEnd: Int): Sequence<Int> = sequence {
    yield(firstEnd)
    var end = firstEnd
    repeat(MAX_EXTRA_WORDS) {
        if (end >= message.length || message[end] != ' ') return@sequence
        var next = end
        while (next < message.length && message[next] == ' ') next++
        while (next < message.length && message[next] != ' ') next++
        end = next
        yield(end)
    }
}

private fun looksLikePath(token: String): Boolean =
    token.any { it == '/' || it == '\\' } ||
        DrivePrefix.containsMatchIn(token) ||
        FileExtension.containsMatchIn(token)

/**
 * Reveal [path] in the platform file manager, selecting the file itself where the platform
 * supports it. Returns `false` when no file manager could be started, so the caller can report it.
 */
fun revealInFileExplorer(path: String): Boolean = runCatching {
    val file = File(path)
    if (isWindows) {
        val argument = if (file.isDirectory) file.path else "/select,${file.path}"
        ProcessBuilder("explorer.exe", argument)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
    } else {
        Desktop.getDesktop().open(if (file.isDirectory) file else file.parentFile ?: file)
    }
}.isSuccess

private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
