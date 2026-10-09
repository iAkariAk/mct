package mct.util.snbt

import arrow.core.partially1

private data class Metadata(
    val type: SnbtType? = null,
)

private enum class IntBase {
    HEX, DEC, BIN;

    fun parseAsByte(str: String) = when (this) {
        HEX -> str.substring(2).toByte(16)
        DEC -> str.toByte(10)
        BIN -> str.substring(2).toByte(2)
    }

    fun parseAsShort(str: String) = when (this) {
        HEX -> str.substring(2).toShort(16)
        DEC -> str.toShort(10)
        BIN -> str.substring(2).toShort(2)
    }

    fun parseAsInt(str: String) = when (this) {
        HEX -> str.substring(2).toInt(16)
        DEC -> str.toInt(10)
        BIN -> str.substring(2).toInt(2)
    }

    fun parseAsLong(str: String) = when (this) {
        HEX -> str.substring(2).toLong(16)
        DEC -> str.toLong(10)
        BIN -> str.substring(2).toLong(2)
    }

    fun parseAsByteOrNull(str: String) = when (this) {
        HEX -> str.substring(2).toByteOrNull(16)
        DEC -> str.toByteOrNull(10)
        BIN -> str.substring(2).toByteOrNull(2)
    }

    fun parseAsShortOrNull(str: String) = when (this) {
        HEX -> str.substring(2).toShortOrNull(16)
        DEC -> str.toShortOrNull(10)
        BIN -> str.substring(2).toShortOrNull(2)
    }

    fun parseAsIntOrNull(str: String) = when (this) {
        HEX -> str.substring(2).toIntOrNull(16)
        DEC -> str.toIntOrNull(10)
        BIN -> str.substring(2).toIntOrNull(2)
    }

    fun parseAsLongOrNull(str: String) = when (this) {
        HEX -> str.substring(2).toLongOrNull(16)
        DEC -> str.toLongOrNull(10)
        BIN -> str.substring(2).toLongOrNull(2)
    }


    fun parse(str: String, snbtType: SnbtType) = when (snbtType) {
        BYTE -> parseAsByte(str)
        SHORT -> parseAsShort(str)
        INT -> parseAsInt(str)
        LONG -> parseAsLong(str)
        else -> error("$snbtType isn't integer")
    }

    fun parseOrNull(str: String, snbtType: SnbtType) = when (snbtType) {
        BYTE -> parseAsByteOrNull(str)
        SHORT -> parseAsShortOrNull(str)
        INT -> parseAsIntOrNull(str)
        LONG -> parseAsLongOrNull(str)
        else -> null
    }
}

fun String.decodeToSnbtTag(): SnbtTag {
    val lexer = SnbtLexer(this)
    val parser = SnbtParser(this, lexer)
    return parser.parse()
}

class SnbtParser(private val snbt: String, private val lexer: SnbtLexer, private val exhaustive: Boolean = false) {
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
        val tag = when (currentToken!!.type) {
            STRING -> parseString()
            L_BRACE -> parseCompound()
            L_BRACKET -> parseList()
            NUMBER -> parseNumber(metadata)
            LITERAL -> parseIdentifier()
            else -> illegalToken("Unexpected token ${currentToken!!.type} at ${currentToken!!.indices}")
        }
        if (exhaustive) expectAny(SnbtTokenType.EOF)
        return tag
    }

    private fun parseCompound(): SnbtCompound {
        val obj = mutableMapOf<String, SnbtTag>()
        val startIndex = currentToken!!.indices.first
        while (currentToken?.type != SnbtTokenType.R_BRACE) {
            val next = advance()
            if (next.type == SnbtTokenType.R_BRACE) return SnbtCompound(
                startIndex..currentToken!!.indices.last,
                obj
            )
            val key = parseString()
            expectAny(SnbtTokenType.COLON)
            advance()
            val value = parseTag()
            obj[key.content] = value
            expectAny(SnbtTokenType.COMMA, SnbtTokenType.R_BRACE)
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
            if (next.type == SnbtTokenType.R_BRACKET) return SnbtList(
                startIndex..currentToken!!.indices.last,
                list
            ) // empty list or tailed comma list
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

        val inferredType = inferTypeFromRaw(raw)
        val expectedType = metadata?.type
        val numStr = raw.dropLast(1).replace("_", "")
        if (expectedType != null && inferredType != null && expectedType != inferredType) parseError("Expected $expectedType, got $inferredType")
        val base: IntBase = when {
            numStr.startsWith("0x") -> HEX
            numStr.startsWith("0b") -> BIN
            else -> DEC
        }
        if (base != DEC && inferredType?.isFloat() == true) parseError("Invalid base $base in $raw")
        val num = when (inferredType) {
            BYTE -> SnbtByte(currentToken!!.indices, base.parseAsByte(numStr))
            SHORT -> SnbtShort(currentToken!!.indices, base.parseAsShort(numStr))
            INT -> SnbtInt(currentToken!!.indices, base.parseAsInt(numStr))
            LONG -> SnbtLong(currentToken!!.indices, base.parseAsLong(numStr))
            FLOAT -> SnbtFloat(currentToken!!.indices, numStr.toFloat())
            DOUBLE -> SnbtDouble(currentToken!!.indices, numStr.toDouble())
            null -> {
                val cleaned = raw.replace("_", "")
                val parsed = tryParseNumber(cleaned, currentToken!!.indices, expectedType, base)
                    ?: illegalNumber("Number $cleaned can be neither an integer nor a double")
                return parsed
            }

            else -> parseError("Illegal type $inferredType")
        }

        return num
    }

    // Refer to https://minecraft.wiki/w/NBT_format#Conversion_from_SNBT
    private inline fun tryParseNumber(
        numStr: String,
        indices: IntRange,
        type: SnbtType? = null,
        base: IntBase
    ): SnbtTag? =
        if (type == null)
            if ('.' in numStr || 'e' in numStr) numStr.toDoubleOrNull()?.let(::SnbtDouble.partially1(indices))
            else base.parseAsIntOrNull(numStr)?.let(::SnbtInt.partially1(indices))
                ?: base.parseAsLongOrNull(numStr)?.let(::SnbtLong.partially1(indices))
        else when (type) {
            BYTE -> base.parseAsByteOrNull(numStr)?.let(::SnbtByte.partially1(indices))
            SHORT -> base.parseAsShortOrNull(numStr)?.let(::SnbtShort.partially1(indices))
            INT -> base.parseAsIntOrNull(numStr)?.let(::SnbtInt.partially1(indices))
            LONG -> base.parseAsLongOrNull(numStr)?.let(::SnbtLong.partially1(indices))
            FLOAT -> numStr.toFloatOrNull()?.let(::SnbtFloat.partially1(indices))
            DOUBLE -> numStr.toDoubleOrNull()?.let(::SnbtDouble.partially1(indices))
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

private fun inferTypeFromRaw(raw: String): SnbtType? {
    val shouldBeFloat = raw.firstOrNull { it !in "+-" } == '.'
    val signOrNull = raw.lastOrNull()?.takeIf(Char::isLetter)?.lowercaseChar()?.takeUnless { it == 'f' && raw.startsWith("0x") }
    val type = signOrNull?.let { sign -> SnbtType.fromSign(sign) ?: parseError("Illegal suffix $signOrNull") }
    if (shouldBeFloat && type?.isFloat() == false) parseError("Expected float/double, got $type from '$raw'")
    return type
}