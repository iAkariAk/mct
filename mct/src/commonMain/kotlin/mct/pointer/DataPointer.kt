package mct.pointer

import arrow.core.getOrElse
import arrow.core.raise.context.Raise
import arrow.core.raise.context.either
import arrow.core.raise.context.ensure
import arrow.core.raise.context.raise
import korlibs.io.util.substringAfterOrNull
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import mct.FormatKind
import mct.MCTError

// {"abc": [{"abc": "def"}]}
// Getting abc >#abc>0>abc
@Serializable(with = DataPointerSerializer::class)
sealed interface DataPointer : Comparable<DataPointer> {
    data object Terminator : DataPointer // Pointer Terminator
    data class Map(val point: String, val value: DataPointer) : DataPointer
    data class List(val point: Int, val value: DataPointer) : DataPointer

    override fun compareTo(other: DataPointer) = encodeToString().compareTo(other.encodeToString())

    companion object
}

data class DataPointerWithValue(val pointer: DataPointer, val value: String, val kind: FormatKind)

inline fun Sequence<DataPointerWithValue>.filterPointer(pattern: DataPointerPattern) =
    filter { (ptr, _) -> pattern.match(ptr) }

inline fun Sequence<DataPointerWithValue>.filterPointer(patterns: Iterable<DataPointerPattern>?) =
    filter { (ptr, _) -> ptr.matches(patterns) }

fun DataPointer.matches(
    patterns: Iterable<DataPointerPattern>?,
): Boolean = patterns?.any { it.match(this) } ?: true

inline fun DataPointer.markMap(point: String) =
    DataPointer.Map(point, this)

inline fun DataPointer.markArray(point: Int) =
    DataPointer.List(point, this)

inline fun DataPointerWithValue.markMap(point: String) =
    copy(pointer = DataPointer.Map(point, pointer))

inline fun DataPointerWithValue.markArray(point: Int) =
    copy(pointer = DataPointer.List(point, pointer))


internal object DataPointerSerializer : KSerializer<DataPointer> {
    val delegatedSerializer = String.serializer()
    override val descriptor: SerialDescriptor =
        SerialDescriptor("mct.pointer.DataPointer", delegatedSerializer.descriptor)

    override fun serialize(encoder: Encoder, value: DataPointer) {
        delegatedSerializer.serialize(encoder, value.encodeToString())
    }

    override fun deserialize(decoder: Decoder) = either {
        DataPointer.decodeFromString(delegatedSerializer.deserialize(decoder))
    }.getOrElse { error(it.message) }
}

object DataPointerBuilderDsl {
    fun terminate() = DataPointer.Terminator
    fun map(point: String, value: DataPointer) = DataPointer.Map(point, value)
    fun array(point: Int, value: DataPointer) = DataPointer.List(point, value)
}

fun DataPointer(builder: DataPointerBuilderDsl.() -> DataPointer) = DataPointerBuilderDsl.run(builder)

class DataPointerParseError(override val message: String) : MCTError

fun DataPointer.encodeTo(sb: StringBuilder) {
    var current = this
    while (current != DataPointer.Terminator) {
        when (current) {
            is DataPointer.List -> {
                sb.append(">${current.point}")
                current = current.value
            }

            is DataPointer.Map -> {
                val escapedPoint = current.point.replace(">", "&>")
                sb.append(">#$escapedPoint")
                current = current.value
            }
        }
    }
}


fun DataPointer.encodeToString() = buildString(::encodeTo)

context(_: Raise<DataPointerParseError>)
fun DataPointer.Companion.decodeFromString(str: String): DataPointer {
    ensure(str.startsWith(">")) {
        DataPointerParseError("Expected '>' at beginning, but found ${str.first()}")
    }
    val segments = mutableListOf<String>()

    var lastIsAmp = false
    val chars = str.toCharArray()
    val buffer = StringBuilder()
    for (i in chars.indices) {
        val c = chars[i]
        if (lastIsAmp) {
            if (c == '>') {
                lastIsAmp = false
                buffer.append(c)
            } else {
                lastIsAmp = false
                buffer.append('&')
            }
            continue
        }

        if (c == '&') {
            lastIsAmp = true
            continue
        }

        if (c == '>') {
            if (buffer.isEmpty()) continue

            val segment = buffer.toString()
            buffer.clear()
            segments.add(segment)
            ensure(i != str.length - 1) {
                DataPointerParseError("DataPointer cannot end with `>`")
            }
            continue
        }

        buffer.append(c)
    }
    segments.add(buffer.toString())

    return segments.foldRight(DataPointer.Terminator) { e, acc: DataPointer ->
        e.substringAfterOrNull('#')?.let {
            DataPointer.Map(it, acc)
        } ?: e.toIntOrNull()?.let {
            DataPointer.List(it, acc)
        } ?: raise(DataPointerParseError("Can't parse $e as an int"))
    }
}
