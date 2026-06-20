package mct.util.snbt

import korlibs.io.util.isLetter
import korlibs.io.util.isLetterOrUnderscore

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

    private fun singleChar(type: SnbtTokenType): SnbtToken {
        return SnbtToken(type, index..index++)
    }

    fun nextToken(): SnbtToken {
        skipWhitespace()
        if (index >= string.length) return SnbtToken(SnbtTokenType.EOF, string.length..<string.length)

        val ch = peek()!!
        return when (ch) {
            '"', '\'' -> readString(ch)
            '-', '.', in '0'..'9' -> readNumber()
            ',' -> singleChar(SnbtTokenType.COMMA)
            ':' -> singleChar(SnbtTokenType.COLON)
            ';' -> singleChar(SnbtTokenType.SEMICOLON)
            '{' -> singleChar(SnbtTokenType.L_BRACE)
            '}' -> singleChar(SnbtTokenType.R_BRACE)
            '[' -> singleChar(SnbtTokenType.L_BRACKET)
            ']' -> singleChar(SnbtTokenType.R_BRACKET)
            '(' -> singleChar(SnbtTokenType.L_PAREN)
            ')' -> singleChar(SnbtTokenType.R_PAREN)
            else -> if (ch.isLetterOrUnderscore()) {
                readLiteral()
            } else parseError("Unknown char: $ch")
        }
    }

    private fun readLiteral(): SnbtToken {
        val start = index

        while (index < string.length) {
            val ch = peek() ?: break
            if (ch == ':') {
                val forwardLooking = peek(1) ?: aheadEOF()
                if (forwardLooking.isLetterOrUnderscore()) {
                    index += 2
                    continue
                } else return SnbtToken(SnbtTokenType.LITERAL, start..<index)
            }
            if (ch.isLetterOrUnderscore()) index++ else break
        }

        return SnbtToken(SnbtTokenType.LITERAL, start..<index)
    }

    private fun readString(boundary: Char): SnbtToken {
        val start = index
        advance()

        while (index < string.length) {
            val ch = advance()

            if (boundary != '\'' && ch == '\\') {
                advance()
                continue
            }

            if (ch == boundary) {
                return SnbtToken(SnbtTokenType.STRING, start..<index)
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
        return SnbtToken(SnbtTokenType.NUMBER, start..<index)
    }
}

private fun Char.isLetterOrUnderscore(): Boolean = this.isLetter() || this == '_'
