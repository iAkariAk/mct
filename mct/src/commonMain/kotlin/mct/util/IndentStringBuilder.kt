package mct.util

class IndentStringBuilder(
    private val sb: StringBuilder = StringBuilder(),
    val indent: String
) {
    var level: Int = 0
        private set

    fun indent() = apply { level++ }
    fun unindent() = apply {
        require(level >= 1) {
            "Cannot unindent when level <= 0"
        }
        level--
    }

    fun appendIndent() = apply {
        if (level > 0) sb.append(indent.repeat(level))
    }

    fun append(char: Char) = apply {
        appendIndent()
        sb.append(char)
    }

    fun append(str: CharSequence) = apply {
        appendIndent()
        sb.append(str)
    }

    fun appendLine() = apply { sb.appendLine() }
    fun appendLine(str: CharSequence) = apply {
        appendIndent()
        sb.appendLine(str)
    }

    fun appendLines(str: CharSequence) = apply {
        str.lines().forEach { line ->
            appendLine(line)
        }
    }

    override fun toString(): String = sb.toString()
}

inline fun IndentStringBuilder.withIndent(block: IndentStringBuilder.() -> Unit) {
    indent()
    block()
    unindent()
}

inline fun IndentStringBuilder.withBlock(
    start: String,
    end: String,
    appendTail: Boolean = true,
    block: IndentStringBuilder.() -> Unit
) {
    append(start)
    appendLine()
    withIndent(block)
    if (appendTail) appendLine()
    append(end)
}

fun buildIndentedString(indent: String = "  ", action: IndentStringBuilder.() -> Unit): String {
    val isb = IndentStringBuilder(StringBuilder(), indent)
    isb.apply(action)
    return isb.toString()
}