package mct.util.snbt


class SnbtLexer(private val string: String) {
    private var index = 0

    private fun skipWhitespace() {
        while (index < string.length) {
            val ch = string[index]
            when (ch) {
                ' ', '\n', '\r', '\t' -> index++
                else -> return
            }
        }
    }

    private fun peek(offset: Int = 0): Char? = string.getOrNull(index + offset)

    private fun advance(): Char = string.getOrNull(index++) ?: aheadEOF()

    private fun skip(n: Int) {
        index += n
        if (index >= string.length) aheadEOF()
    }

    private fun singleChar(type: SnbtTokenType): SnbtToken {
        return SnbtToken(type, index..index++)
    }

    fun nextToken(): SnbtToken {
        skipWhitespace()
        if (index >= string.length) return SnbtToken(EOF, string.length..<string.length)

        val ch = peek()!!
        return when (ch) {
            '"', '\'' -> readString(ch)
            '.' -> if (peek(1)?.isDigit() ?: false) readNumber() else readLiteral()
            '-', in '0'..'9' -> readNumber()
            ',' -> singleChar(COMMA)
            ':' -> singleChar(COLON)
            ';' -> singleChar(SEMICOLON)
            '{' -> singleChar(L_BRACE)
            '}' -> singleChar(R_BRACE)
            '[' -> singleChar(L_BRACKET)
            ']' -> singleChar(R_BRACKET)
            '(' -> singleChar(L_PAREN)
            ')' -> singleChar(R_PAREN)
            else -> if (ch.isIdentifier()) {
                readLiteral()
            } else parseError("Unknown char: $ch")
        }
    }

    private fun readLiteral(): SnbtToken {
        val start = index

        while (index < string.length) {
            val ch = peek() ?: break
            if (ch.isIdentifierOrDigit()) index++ else break
        }

        return SnbtToken(LITERAL, start..<index)
    }

    private fun readString(boundary: Char): SnbtToken {
        val start = index
        advance()

        while (index < string.length) {
            val ch = advance()

            if (ch == '\\') when (boundary) {
                '"' -> {
                    advance()
                    continue
                }

                '\'' -> {
                    peek()?.let {
                        if (it in "\\'") {
                            advance()
                            continue
                        }
                    }
                }
            }

            if (ch == boundary) {
                return SnbtToken(STRING, start..<index)
            }
        }

        aheadEOF()
    }

    private fun readNumber(): SnbtToken {
        val start = index

        while (index < string.length) {
            val ch = peek() ?: break

            if (ch.isLetterOrDigit() || ch in "_-+.") {
                index++
                continue
            } else {
                break
            }
        }
        return SnbtToken(NUMBER, start..<index)
    }
}

private fun Char.isLetterOrUnderscore() = this.isLetter() || this == '_'
private fun Char.isIdentifier() = this.isLetter() || this in "._-"
private fun Char.isIdentifierOrDigit() = this.isLetterOrDigit() || this in "._-"