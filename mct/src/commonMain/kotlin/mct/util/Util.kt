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


fun NbtTag.toSnbt(pretty: Boolean = false): String = (if(pretty) PrettySnbt else Snbt).encodeToString(this)

inline infix fun Byte.divCeil(other: Byte) = (this + other - 1) / other
inline infix fun Short.divCeil(other: Short) = (this + other - 1) / other
inline infix fun Int.divCeil(other: Int) = (this + other - 1) / other
inline infix fun Long.divCeil(other: Long) = (this + other - 1) / other
inline infix fun UByte.divCeil(other: UByte) = (this + other - 1u) / other
inline infix fun UShort.divCeil(other: UShort) = (this + other - 1u) / other
inline infix fun UInt.divCeil(other: UInt) = (this + other - 1u) / other
inline infix fun ULong.divCeil(other: ULong) = (this + other - 1u) / other

@DslMarker
internal annotation class BuilderMaker