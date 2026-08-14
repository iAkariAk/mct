package mct.util.snbt

import mct.model.patch.SnbtSyntaxKind
import mct.util.doubleUnquoted
import mct.util.formatir.*
import mct.util.singleQuoted
import mct.util.unreachable

sealed interface SnbtTag {
    val indices: IntRange

    fun toIR(): IRElement

    fun encodeTo(builder: StringBuilder)

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
    override fun encodeTo(builder: StringBuilder) {
        if (value) builder.append("1b") else builder.append("0b")
    }
}

data class SnbtByte(override val indices: IntRange, val value: Byte) : SnbtTag {
    override fun toIR() = IRByte(value)
    override fun encodeTo(builder: StringBuilder) {
        builder.append("${value}b")
    }
}

data class SnbtShort(override val indices: IntRange, val value: Short) : SnbtTag {
    override fun toIR() = IRShort(value)
    override fun encodeTo(builder: StringBuilder) {
        builder.append(value)
    }
}

data class SnbtInt(override val indices: IntRange, val value: Int) : SnbtTag {
    override fun toIR() = IRInt(value)
    override fun encodeTo(builder: StringBuilder) {
        builder.append(value)
    }
}

data class SnbtLong(override val indices: IntRange, val value: Long) : SnbtTag {
    override fun toIR() = IRLong(value)
    override fun encodeTo(builder: StringBuilder) {
        builder.append(value)
    }
}

data class SnbtFloat(override val indices: IntRange, val value: Float) : SnbtTag {
    override fun toIR() = IRFloat(value)
    override fun encodeTo(builder: StringBuilder) {
        builder.append(value)
    }
}

data class SnbtDouble(override val indices: IntRange, val value: Double) : SnbtTag {
    override fun toIR() = IRDouble(value)
    override fun encodeTo(builder: StringBuilder) {
        builder.append(value)
    }
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
    override fun encodeTo(builder: StringBuilder) {
        builder.append(raw)
    }
}

data class SnbtCompound(override val indices: IntRange, val value: Map<String, SnbtTag>) : SnbtTag,
    Map<String, SnbtTag> by value {
    override fun toIR() = IRObject(value.mapValues { it.value.toIR() })
    override fun encodeTo(builder: StringBuilder) {
        builder.append('{')
        var count = 0
        for ((key, value) in value.entries) {
            if (++count > 1) builder.append(",")
            builder.append(key.singleQuoted())
            builder.append(':')
            value.encodeTo(builder)
        }
        builder.append('}')
    }
}

data class SnbtList(
    override val indices: IntRange,
    val value: List<SnbtTag>,
) : SnbtTag, List<SnbtTag> by value {
    override fun toIR() = IRList(value.map { it.toIR() })
    override fun encodeTo(builder: StringBuilder) {
        builder.append('[')
        var count = 0
        for (element in value) {
            if (++count > 1) builder.append(",")
            element.encodeTo(builder)
        }
        builder.append(']')
    }
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