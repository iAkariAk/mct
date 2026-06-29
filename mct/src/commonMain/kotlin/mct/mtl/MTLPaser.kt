package mct.mtl

import mct.util.unreachable

class MTLPaser(private val lexer: MTLLexer, private val str: String) {
    companion object {
        fun parse(mtl: String): Sequence<MTLMapping> {
            val lexer = MTLLexer(mtl)
            val parser = MTLPaser(lexer, mtl)

            return sequence {
                var next = parser.nextMapping()
                while (next != null) {
                    yield(next)
                    next = parser.nextMapping()
                }
            }
        }
    }

    private var _currentToken: MTLToken? = null
    private var currentToken: MTLToken
        get() = _currentToken!!
        set(value) {
            _currentToken = value
        }

    private inline fun currentView() = str.substring(currentToken.indices)
    private fun nextToken(): MTLToken {
        _currentToken = lexer.nextToken()
        return currentToken
    }

    private fun require(vararg kinds: MTLTokenKind) {
        if (currentToken.kind !in kinds) {
            parseError("Expected ${kinds.joinToString(" | ")}, but got ${currentToken.kind}", currentToken.indices)
        }
    }

    private fun expect(vararg kinds: MTLTokenKind): MTLToken {
        val next = nextToken()
        require(*kinds)
        return next
    }

    private fun skipNewline(): Boolean {
        var isNexted = false
        if (_currentToken == null) {
            nextToken()
            isNexted = true
        }
        while (currentToken.kind == MTLTokenKind.NEWLINE) {
            isNexted = true
            nextToken()
        }
        return isNexted
    }

    fun nextMapping(): MTLMapping? {
        if (!skipNewline()) nextToken()
        if (currentToken.kind == EOF) return null
        val lvalue = parseExpr()
        expect(ARROW)
        nextToken()
        val rvalue = parseExpr()
        expect(NEWLINE, EOF)

        val startIndex = lvalue.indices!!.first
        val endIndex = rvalue.indices!!.last
        return MTLMapping(startIndex..endIndex, lvalue, rvalue)
    }

    private fun parseExpr(): MTLExpression = when (currentToken.kind) {
        EOF -> aheadEOF(currentToken.indices)
        LITERAL -> parseLiteral()
        L_PARENTHESIS -> parsePair()
        L_BRACKET -> parseList()
        NEWLINE -> unreachable
        ARROW -> parseError("There shouldn't be ARROW(==>) at ${currentToken.indices}.", currentToken.indices)
        else -> parseError("Unexpected token $_currentToken at ${currentToken.indices}", currentToken.indices)
    }

    private fun parseLiteral(): MTLLiteral {
        require(currentToken.kind == LITERAL)
        val unescaped = currentView().unescapeMTLLiteral()
        return MTLLiteral(currentToken.indices, unescaped)
    }

    private fun parsePair(): MTLPair {
        require(currentToken.kind == L_PARENTHESIS)
        val startIndex = currentToken.indices.first
        nextToken()
        skipNewline()
        val lvalue = parseExpr()
        expect(NEWLINE)
        nextToken()
        val rvalue = parseExpr()
        nextToken()
        val next = if (skipNewline()) {
            require(R_PARENTHESIS)
            currentToken
        } else {
            expect(R_PARENTHESIS)
        }
        val endIndex = next.indices.last
        return MTLPair(startIndex..endIndex, lvalue, rvalue)
    }

    private fun parseList(): MTLList {
        require(currentToken.kind == MTLTokenKind.L_BRACKET)
        val startIndex = currentToken.indices.first
        var endIndex = startIndex
        nextToken()
        val list = mutableListOf<MTLExpression>()
        while (currentToken.kind != R_BRACKET) {
            skipNewline()
            if (currentToken.kind == MTLTokenKind.R_BRACKET) {
                endIndex = currentToken.indices.last
                break
            }
            val element = parseExpr()
            list.add(element)
            val next = expect(NEWLINE, R_BRACKET)
            endIndex = next.indices.last
        }
        return MTLList(startIndex..endIndex, list)

    }
}