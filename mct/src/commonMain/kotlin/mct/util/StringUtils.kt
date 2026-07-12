package mct.util

import kotlin.math.roundToInt

private val ESCAPE_REGEX = """\\(.)""".toRegex2()

fun String.unescaped() = ESCAPE_REGEX.replace(this) {
    when (val r = it.groupValues[1]) {
        "n" -> "\n"
        "r" -> "\r"
        "t" -> "\t"
        "\"" -> "\""
        else -> r
    }
}

fun String.escaped(): String {
    val sb = StringBuilder(length shl 2)
    for (ch in this) {
        when (ch) {
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            else -> sb.append(ch)
        }
    }
    return sb.toString()
}

fun String.doubleQuoted() = """"${escaped()}""""
fun String.singleQuoted(): String {
    require('\'' !in this) {
        "Cannot quote '"
    }
    return "'$this'"
}

fun String.singleUnquoted(): String {
    require(surroundedBy('\'')) {
        "Unquoting require string surrounded by '"
    }
    return substring(1, length - 1).unescaped()
}

fun String.doubleUnquoted(): String {
    require(surroundedBy('"')) {
        "Unquoting require string surrounded by \""
    }
    return substring(1, length - 1).unescaped()
}

fun String.surroundedBy(first: Char, last: Char): Boolean =
    length >= 2 && startsWith(first) && endsWith(last)

fun String.surroundedBy(first: String, last: String): Boolean =
    length >= first.length + last.length && startsWith(first) && endsWith(last)


fun String.surroundedBy(separator: Char): Boolean =
    length >= 2 && startsWith(separator) && endsWith(separator)

fun String.surroundedBy(separator: String): Boolean =
    length >= separator.length * 2 && startsWith(separator) && endsWith(separator)

fun String.substringAfterOrNull(delimiter: Char): String? {
    val index = indexOf(delimiter)
    return if (index == -1) null else substring(index + 1, length)
}

fun String.substringAfterOrNull(delimiter: String): String? {
    val index = indexOf(delimiter)
    return if (index == -1) null else substring(index + 1, length)
}

fun StringBuilder.appendEscaped(string: CharSequence) {
    ensureCapacity(length + (string.length * 1.1).roundToInt())

    for (ch in string) {
        when (ch) {
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\\' -> append("\\\\")
            else -> append(ch)
        }
    }
}

fun CharSequence.removePrefixOrNull(prefix: CharSequence): CharSequence? =
    if (startsWith(prefix)) subSequence(prefix.length, length)
    else null


fun CharSequence.removeSuffixOrNull(suffix: CharSequence): CharSequence? =
    if (endsWith(suffix)) subSequence(0, length - suffix.length)
    else null
