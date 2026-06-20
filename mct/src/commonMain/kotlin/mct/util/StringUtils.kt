package mct.util

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

fun String.surroundedBy(separator: Char): Boolean =
    length >= 2 && startsWith(separator) && endsWith(separator)

fun String.surroundedBy(separator: String): Boolean =
    length >= separator.length * 2 && startsWith(separator) && endsWith(separator)
