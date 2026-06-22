package mct.util.snbt

import korlibs.io.lang.unreachable
import mct.model.patch.SnbtSyntaxKind
import mct.util.doubleUnquoted
import mct.util.formatir.*

sealed interface SnbtTag {
    val indices: IntRange

    fun toIR(): IRElement

    companion object {
        fun decodeFromString(snbt: String): SnbtTag {
            val lexer = SnbtLexer(snbt)
            val parser = SnbtParser(snbt, lexer)
            return parser.parse()
        }
    }
}

data class SnbtBoolean(override val indices: IntRange, val value: Boolean) : SnbtTag {
    override fun toIR() = IRBoolean(value)
}

data class SnbtByte(override val indices: IntRange, val value: Byte) : SnbtTag {
    override fun toIR() = IRByte(value)
}

data class SnbtShort(override val indices: IntRange, val value: Short) : SnbtTag {
    override fun toIR() = IRShort(value)
}

data class SnbtInt(override val indices: IntRange, val value: Int) : SnbtTag {
    override fun toIR() = IRInt(value)
}

data class SnbtLong(override val indices: IntRange, val value: Long) : SnbtTag {
    override fun toIR() = IRLong(value)
}

data class SnbtFloat(override val indices: IntRange, val value: Float) : SnbtTag {
    override fun toIR() = IRFloat(value)
}

data class SnbtDouble(override val indices: IntRange, val value: Double) : SnbtTag {
    override fun toIR() = IRDouble(value)
}

data class SnbtString(override val indices: IntRange, val raw: String, val boundary: Char?) : SnbtTag {
    val content = when (boundary) {
        '"' -> raw.doubleUnquoted()
        '\'' -> raw.substring(1, raw.length - 1)
        null -> raw
        else -> unreachable
    }

    val syntaxKind = when (boundary) {
        '"' -> SnbtSyntaxKind.DoubleQuoteString
        '\'' -> SnbtSyntaxKind.SingleQuoteString
        null -> SnbtSyntaxKind.LiteralString
        else -> unreachable
    }

    override fun toIR() = IRString(content)
}

data class SnbtCompound(override val indices: IntRange, val value: Map<String, SnbtTag>) : SnbtTag,
    Map<String, SnbtTag> by value {
    override fun toIR() = IRObject(value.mapValues { it.value.toIR() })
}

data class SnbtList(
    override val indices: IntRange,
    val value: List<SnbtTag>,
) : SnbtTag, List<SnbtTag> by value {
    override fun toIR() = IRList(value.map { it.toIR() })
}

enum class SnbtType {
    BOOLEAN,
    BYTE,
    SHORT,
    INT,
    LONG,
    FLOAT,
    DOUBLE,
    STRING,
    COMPOUND,
    LIST;

    companion object {
        fun fromSign(sign: Char): SnbtType? = when (sign.lowercaseChar()) {
            'b' -> BYTE
            's' -> SHORT
            'i' -> INT
            'l' -> LONG
            'f' -> FLOAT
            'd' -> DOUBLE
            else -> null
        }
    }
}