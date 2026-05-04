package mct.util

import kotlinx.serialization.encodeToString
import mct.serializer.PrettySnbt
import mct.serializer.Snbt
import net.benwoodworth.knbt.NbtTag

val unreachable: Nothing get() = error("Unreachable")

inline fun <T> ArrayDeque<T>.top() = last()
inline fun <T> ArrayDeque<T>.bottom() = first()
inline fun <T> ArrayDeque<T>.peek() = last()
inline fun <T> ArrayDeque<T>.pop() = removeLast()
inline fun <T> ArrayDeque<T>.push(element: T) = addLast(element)
inline fun <T> ArrayDeque<T>.peekOrNull() = lastOrNull()
inline fun <T> ArrayDeque<T>.popOrNull() = removeLastOrNull()


fun NbtTag.toSnbt(pretty: Boolean = false): String = (if (pretty) PrettySnbt else Snbt).encodeToString(this)

inline infix fun Byte.divCeil(other: Byte) = (this + other - 1) / other
inline infix fun Short.divCeil(other: Short) = (this + other - 1) / other
inline infix fun Int.divCeil(other: Int) = (this + other - 1) / other
inline infix fun Long.divCeil(other: Long) = (this + other - 1) / other
inline infix fun UByte.divCeil(other: UByte) = (this + other - 1u) / other
inline infix fun UShort.divCeil(other: UShort) = (this + other - 1u) / other
inline infix fun UInt.divCeil(other: UInt) = (this + other - 1u) / other
inline infix fun ULong.divCeil(other: ULong) = (this + other - 1u) / other

inline fun String.findAll(str: String): Sequence<IntRange> = sequence {
    var index = 0
    while (index < length) {
        index = indexOf(str, index)
        if (index  == -1) break
        val end = index + str.length
        yield(index until end)
        index = end
    }
}

interface StringIndices {
    val indices: IntRange
    val content: String
}

fun StringIndices(indices: IntRange, content: String): StringIndices = StringIndicesImpl(indices, content)

private class StringIndicesImpl(
    override val indices: IntRange,
    override val content: String
) : StringIndices {
    override fun toString(): String {
        return "StringIndices(indices=$indices, content='$content')"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as StringIndicesImpl

        if (indices != other.indices) return false
        if (content != other.content) return false

        return true
    }

    override fun hashCode(): Int {
        var result = indices.hashCode()
        result = 31 * result + content.hashCode()
        return result
    }
}

@DslMarker
annotation class BuilderMaker