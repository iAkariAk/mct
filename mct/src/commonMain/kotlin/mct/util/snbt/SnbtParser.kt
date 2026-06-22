package mct.util.snbt

import arrow.core.partially1

private data class Metadata(
    val type: SnbtType? = null,
)

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

    private fun parseTag(metadata: Metadata? = null): SnbtTag {
//        println(currentToken)
        val tag = when (currentToken!!.type) {
            SnbtTokenType.STRING -> parseString()
            SnbtTokenType.L_BRACE -> parseCompound()
            SnbtTokenType.L_BRACKET -> parseList()
            SnbtTokenType.NUMBER -> parseNumber(metadata)
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
            println(key)
            expectAny(SnbtTokenType.COLON)
            advance()
            val value = parseTag()
            println(value)
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
        var type: SnbtType? = null
        while (currentToken?.type != SnbtTokenType.R_BRACKET) {
            val next = advance()
            if (i == 0 && next.type == SnbtTokenType.R_BRACKET) return SnbtList(
                startIndex..startIndex + 1,
                emptyList()
            )
            val value = parseTag(Metadata(type))
            list += value
            expectAny({ next2 ->
                (i == 0 && next2.type == SnbtTokenType.SEMICOLON && value is SnbtString && value.indices.first == value.indices.last && value.raw.single() in "BIL").also {
                    if (it) {
                        value as SnbtString
                        list.removeLast()
                        type = SnbtType.fromSign(value.raw.single())
                    }
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


    private fun parseNumber(metadata: Metadata? = null): SnbtTag {
        val raw = currentView()

        val suffix = raw.takeLastWhile(Char::isLetter).lowercase()

        if (suffix.length > 1) parseError("Illegal suffix $suffix")
        val signOrNull = suffix.firstOrNull()
        val inferredType =
            signOrNull?.let { sign -> SnbtType.fromSign(sign) ?: parseError("Illegal suffix $signOrNull") }
        val expectedType = metadata?.type
        val numStr = raw.dropLast(1).replace("_", "")
        if (expectedType != null && inferredType != null && expectedType != inferredType) parseError("Expected $expectedType, got $inferredType")
        val num = when (inferredType) {
            SnbtType.BYTE -> SnbtByte(currentToken!!.indices, numStr.toByte())
            SnbtType.SHORT -> SnbtShort(currentToken!!.indices, numStr.toShort())
            SnbtType.INT -> SnbtInt(currentToken!!.indices, numStr.toInt())
            SnbtType.LONG -> SnbtLong(currentToken!!.indices, numStr.toLong())
            SnbtType.FLOAT -> SnbtFloat(currentToken!!.indices, numStr.toFloat())
            SnbtType.DOUBLE -> SnbtDouble(currentToken!!.indices, numStr.toDouble())
            null -> {
                val cleaned = raw.replace("_", "")
                val parsed = tryParseNumber(cleaned, currentToken!!.indices, expectedType)
                    ?: illegalNumber("Number $cleaned can be neither an integer nor a double")
                return parsed
            }

            else -> parseError("Illegal suffix $suffix")
        }

        return num
    }

    // Refer to https://minecraft.wiki/w/NBT_format#Conversion_from_SNBT
    private inline fun tryParseNumber(numStr: String, indices: IntRange, type: SnbtType? = null): SnbtTag? =
        if (type == null)
            if ('.' in numStr) numStr.toDoubleOrNull()?.let(::SnbtDouble.partially1(indices))
            else numStr.toIntOrNull()?.let(::SnbtInt.partially1(indices))
        else when (type) {
            SnbtType.BYTE -> numStr.toByteOrNull()?.let(::SnbtByte.partially1(indices))
            SnbtType.SHORT -> numStr.toShortOrNull()?.let(::SnbtShort.partially1(indices))
            SnbtType.INT -> numStr.toIntOrNull()?.let(::SnbtInt.partially1(indices))
            SnbtType.LONG -> numStr.toLongOrNull()?.let(::SnbtLong.partially1(indices))
            SnbtType.FLOAT -> numStr.toFloatOrNull()?.let(::SnbtFloat.partially1(indices))
            SnbtType.DOUBLE -> numStr.toDoubleOrNull()?.let(::SnbtDouble.partially1(indices))
            else -> null
        }

    private fun parseIdentifier(): SnbtTag {
        return when (val literal = currentView()) {
            "true" -> SnbtBoolean(currentToken!!.indices, true)
            "false" -> SnbtBoolean(currentToken!!.indices, false)
            else -> SnbtString(currentToken!!.indices, literal, null)
        }
    }
}