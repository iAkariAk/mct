package mct.mtl

internal fun String.escapeMTLLiteral() = buildString {
    val content = this@escapeMTLLiteral
    append('|')
    content.forEach { ch ->
        when (ch) {
            '|', '&' -> append('&')
        }
        append(ch)
    }
    append('|')
}

internal fun String.unescapeMTLLiteral() = buildString {
    val raw = this@unescapeMTLLiteral
    var i = 1
    while (i < raw.lastIndex) {
        val ch = raw[i]
        if (ch == '&') {
            i += 2
            continue
        }
        append(ch)
        i++
    }
}
