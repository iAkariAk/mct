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

internal fun String.unwrappedMTLLiteral() = buildString {
    val raw = this@unwrappedMTLLiteral
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

internal fun String.unescapedMTLLiteral() = buildString {
    val raw = this@unescapedMTLLiteral
    var i = 0
    while (i < raw.length) {
        val ch = raw[i]
        if (ch == '&') {
            i += 2
            continue
        }
        append(ch)
        i++
    }
}
