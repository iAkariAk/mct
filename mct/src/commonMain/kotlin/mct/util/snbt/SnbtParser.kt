package mct.util.snbt


class SnbtParser(private val snbt: String, private val lexer: SnbtLexer) {
    private var currentToken: SnbtToken? = null

    private fun advance() = nextToken() ?: aheadEOF()
    private fun substring(range: IntRange) = snbt.substring(range)
    private fun currentView() = substring(currentToken!!.indices)

    private inline fun expectAny(condition: (SnbtToken) -> Boolean, vararg types: SnbtTokenType): SnbtToken =
        advance().also { token ->
            if (!condition(token) && types.none { it == token.type }) illegalToken(
                "Expected ${types.contentToString()}, but found ${token.type} at ${token.indices}"
            )
        }

    private fun expectAny(vararg types: SnbtTokenType): SnbtToken = expectAny({ true }, *types)

    private fun nextToken(): SnbtToken? {
        currentToken = lexer.nextToken()
        return currentToken
    }

    fun parse(): SnbtTag {
        currentToken = nextToken()
        return parseTag()
    }

    private fun parseTag(): SnbtTag {
        val tag = when (currentToken!!.type) {
            SnbtTokenType.STRING -> parseString()
            SnbtTokenType.L_BRACE -> parseCompound()
            SnbtTokenType.L_BRACKET -> parseList()
            SnbtTokenType.NUMBER -> parseNumber()
            SnbtTokenType.LITERAL -> parseIdentifier()
            else -> illegalToken("Unexpected token ${currentToken!!.type} at ${currentToken!!.indices}")
        }
        return tag
    }

    private fun parseCompound(): SnbtCompound {
        val obj = mutableMapOf<String, SnbtTag>()
        val startIndex = currentToken!!.indices.first
        var i = 0
        while (currentToken?.type != SnbtTokenType.R_BRACE) {
            val next = advance()
            if (i == 0 && next.type == SnbtTokenType.R_BRACE) return SnbtCompound(
                startIndex..startIndex + 1,
                emptyMap()
            )
            val key = parseString()
            expectAny(SnbtTokenType.COLON)
            advance()
            val value = parseTag()
            obj[key.content] = value
            expectAny(SnbtTokenType.COMMA, SnbtTokenType.R_BRACE)
            i++
        }
        val endIndex = currentToken!!.indices.last

        return SnbtCompound(startIndex..endIndex, obj)
    }

    private fun parseList(): SnbtList {
        val list = mutableListOf<SnbtTag>()
        val startIndex = currentToken!!.indices.first
        var i = 0
        while (currentToken?.type != SnbtTokenType.R_BRACKET) {
            val next = advance()
            if (i == 0 && next.type == SnbtTokenType.R_BRACKET) return SnbtList(
                startIndex..startIndex + 1,
                emptyList()
            )
            val value = parseTag()
            list += value
            expectAny({ next2 ->
                (i == 0 && next2.type == SnbtTokenType.SEMICOLON && value is SnbtString && value.indices.first == value.indices.last && value.raw.single() in "BIL").also {
                    if (it) list.removeLast()
                } // skip the type prefix of typed array
            }, SnbtTokenType.COMMA, SnbtTokenType.R_BRACKET)
            i++
        }
        val endIndex = currentToken!!.indices.last

        return SnbtList(startIndex..endIndex, list)
    }


    private fun parseString(): SnbtString = when (currentToken!!.type) {
        SnbtTokenType.STRING -> {
            val raw = currentView()
            val boundary = raw.first()
            check(boundary in "'\"")
            SnbtString(currentToken!!.indices, raw, boundary)
        }

        SnbtTokenType.LITERAL -> {
            val raw = currentView()
            SnbtString(currentToken!!.indices, raw, null)
        }

        else -> parseError("$currentToken(${currentView()}) isn't a string")
    }


    private fun parseNumber(): SnbtTag {
        val raw = currentView()

        val suffix = raw.takeLastWhile(Char::isLetter).lowercase()

        if (suffix.length > 1) parseError("Illegal suffix $suffix")
        val dropLast = raw.dropLast(1).replace("_", "")
        val num = when (suffix.firstOrNull()) {
            'b' -> SnbtByte(currentToken!!.indices, dropLast.toByte())
            's' -> SnbtShort(currentToken!!.indices, dropLast.toShort())
            null -> SnbtInt(currentToken!!.indices, raw.replace("_", "").toInt())
            'i' -> SnbtInt(currentToken!!.indices, dropLast.toInt())
            'l' -> SnbtLong(currentToken!!.indices, dropLast.toLong())
            'f' -> SnbtFloat(currentToken!!.indices, dropLast.toFloat())
            'd' -> SnbtDouble(currentToken!!.indices, dropLast.toDouble())
            else -> parseError("Illegal suffix $suffix")
        }

        return num
    }

    private fun parseIdentifier(): SnbtTag {
        return when (val literal = currentView()) {
            "true" -> SnbtBoolean(currentToken!!.indices, true)
            "false" -> SnbtBoolean(currentToken!!.indices, false)
            else -> SnbtString(currentToken!!.indices, literal, null)
        }
    }
}