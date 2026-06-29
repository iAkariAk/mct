package mct.mtl

import mct.util.unreachable

enum class MTLTokenKind {
    LITERAL,
    L_PARENTHESIS,
    R_PARENTHESIS,
    L_BRACKET,
    R_BRACKET,
    ARROW,
    NEWLINE,
    EOF,
}

data class MTLToken(val kind: MTLTokenKind, val indices: IntRange)

class MTLLexer(private val str: String) {
    private var cursor = 0
    private var lineCount = 0
    private inline fun peek(offset: Int = 0): Char? = str.getOrNull(cursor + offset)
    private inline fun advance(n: Int = 1) {
        cursor += n
    }

    private fun skipWhitespace() {
        while (cursor < str.length) {
            val ch = str[cursor]
            when (ch) {
                '\t', ' ' -> cursor++
                else -> break
            }
        }
    }

    private fun skipComment() {
        if (peek() != '#') return
        while (cursor < str.length) {
            val ch = str[cursor]
            when (ch) {
                '\n', '\r' -> break
                else -> cursor++
            }
        }
    }

    private fun currentCursorAsRange() = cursor..cursor

    fun nextToken(): MTLToken {
        skipWhitespace()
        skipComment()

        if (cursor >= str.length) return MTLToken(EOF, currentCursorAsRange())

        return when (val ch = peek()) {
            '\n', '\r' -> readNewLine()
            '|' -> readLiteral()
            '=' -> readArrow() // ==>
            '[' -> readSingleChar(L_BRACKET)
            ']' -> readSingleChar(R_BRACKET)
            '(' -> readSingleChar(L_PARENTHESIS)
            ')' -> readSingleChar(R_PARENTHESIS)
            else -> parseError("Unknown char $ch, if it's a literal, please wrap it by |...|", currentCursorAsRange())
        }
    }

    private fun readSingleChar(kind: MTLTokenKind): MTLToken = MTLToken(kind, currentCursorAsRange()).also {
        advance()
    }

    private fun readNewLine(): MTLToken {
        lineCount++
        return when (peek()) {
            '\n' -> {
                advance()
                MTLToken(NEWLINE, currentCursorAsRange())
            }

            '\r' -> {
                val next = peek(1)
                if (next == '\n') {
                    advance(2)
                    MTLToken(NEWLINE, cursor - 2..cursor)
                } else {
                    advance(1)
                    MTLToken(NEWLINE, cursor - 1..cursor)
                }
            }

            else -> unreachable
        }
    }

    private fun readLiteral(): MTLToken {
        val startCursor = cursor
        advance()
        while (cursor < str.length) {
            val ch = peek()
            advance()
            if (ch == '&') {
                val next = peek(1)
                if (next != null) {
                    advance(1)
                    continue
                } else parseError("Unclosed literal", startCursor..cursor)
            }
            if (ch == '|') {
                return MTLToken(MTLTokenKind.LITERAL, startCursor..<cursor)
            }
        }
        parseError("Unclosed literal", startCursor..cursor)
    }

    private fun readArrow(): MTLToken {
        if (cursor + 3 >= str.length) parseError("Incompleted arrow ==>", currentCursorAsRange())
        advance(3)
        val indices = cursor - 3..<cursor
        val actual = str.substring(indices)
        if (actual != "==>") parseError("Expected ==>, but got $actual", indices)
        return MTLToken(ARROW, indices)
    }
}

internal fun MTLLexer.asSequence() = sequence {
    var token: MTLToken
    do {
        token = nextToken()
        yield(token)
    } while (token.kind != EOF)
}