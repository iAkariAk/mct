package mct.util

import arrow.core.Either
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import mct.serializer.MCTJson
import mct.serializer.PrettyJson
import mct.serializer.PrettySnbt
import mct.serializer.Snbt
import mct.util.snbt.SnbtTag
import mct.util.snbt.decodeToSnbtTag
import net.benwoodworth.knbt.NbtTag

val unreachable: Nothing get() = error("Unreachable")

inline fun <T> ArrayDeque<T>.top() = last()
inline fun <T> ArrayDeque<T>.bottom() = first()
inline fun <T> ArrayDeque<T>.peek() = last()
inline fun <T> ArrayDeque<T>.pop() = removeLast()
inline fun <T> ArrayDeque<T>.push(element: T) = addLast(element)
inline fun <T> ArrayDeque<T>.peekOrNull() = lastOrNull()
inline fun <T> ArrayDeque<T>.popOrNull() = removeLastOrNull()

fun JsonElement.toJson(pretty: Boolean = false): String = (if (pretty) PrettyJson else MCTJson).encodeToString(this)
fun NbtTag.toSnbt(pretty: Boolean = false): String = (if (pretty) PrettySnbt else Snbt).encodeToString(this)
fun String.toSnbtNbtTagOrNull(): SnbtTag? {
    val precondition = surroundedBy('"') || surroundedBy('\'') || surroundedBy('[', ']') || surroundedBy('{', '}')
    return if (precondition) runCatching { this.decodeToSnbtTag() }.getOrNull() else null
}

fun String.toJsonElementOrNull(json: Either<Json, NonstandardJson> = StandardJsonLeft): JsonElement? {
    val precondition =
        surroundedBy('"') || (json.isRight { it.allowSingleQuote } && surroundedBy('\''))
                || surroundedBy('[', ']') || surroundedBy('{', '}')
    return if (precondition) runCatching {
        json.fold(
            ifLeft = { it.decodeFromString<JsonElement>(this) },
            ifRight = { it.decodeFromString<JsonElement>(this) }
        )
    }.getOrNull() else null
}

fun String.isSnbt() = toSnbtNbtTagOrNull() != null
fun String.isJson(json: Either<Json, NonstandardJson> = StandardJsonLeft) = toJsonElementOrNull(json) != null

inline infix fun Byte.divCeil(other: Byte) = (this + other - 1) / other
inline infix fun Short.divCeil(other: Short) = (this + other - 1) / other
inline infix fun Int.divCeil(other: Int) = (this + other - 1) / other
inline infix fun Long.divCeil(other: Long) = (this + other - 1) / other
inline infix fun UByte.divCeil(other: UByte) = (this + other - 1u) / other
inline infix fun UShort.divCeil(other: UShort) = (this + other - 1u) / other
inline infix fun UInt.divCeil(other: UInt) = (this + other - 1u) / other
inline infix fun ULong.divCeil(other: ULong) = (this + other - 1u) / other

inline infix fun IntRange.overlapsWith(other: IntRange) = maxOf(first, other.first) <= minOf(last, other.last)

inline fun String.findAll(str: String): Sequence<IntRange> = sequence {
    var index = 0
    while (index < length) {
        index = indexOf(str, index)
        if (index == -1) break
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

inline fun IntRange.offset(offset: Int) = if (offset != 0) (first + offset)..(last + offset) else this

@DslMarker
annotation class BuilderMaker