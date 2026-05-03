package mct.serializer

import korlibs.io.lang.unreachable
import kotlinx.serialization.json.*
import net.benwoodworth.knbt.*

fun NbtTag.toJsonElement(): JsonElement = when (this) {
    is NbtByte -> JsonPrimitive(value)
    is NbtDouble -> JsonPrimitive(value)
    is NbtFloat -> JsonPrimitive(value)
    is NbtInt -> JsonPrimitive(value)
    is NbtLong -> JsonPrimitive(value)
    is NbtShort -> JsonPrimitive(value)
    is NbtString -> JsonPrimitive(value)
    is NbtCompound -> JsonObject(mapValues { it.value.toJsonElement() })
    is NbtByteArray -> JsonArray(map { JsonPrimitive(it) })
    is NbtLongArray -> JsonArray(map { JsonPrimitive(it) })
    is NbtIntArray -> JsonArray(map { JsonPrimitive(it) })
    is NbtList<*> -> JsonArray(map { it.toJsonElement() })
}

fun JsonElement.toNbtCompound(): NbtTag = when (this) {
    is JsonArray -> map { it.toNbtCompound() }.asNbtListUnsafe()
    is JsonObject -> NbtCompound(mapValues { it.value.toNbtCompound() })
    is JsonPrimitive -> if (isString) NbtString(content) else
        (intOrNull?.let(::NbtInt)
            ?: longOrNull?.let(::NbtLong)
            ?: floatOrNull?.let(::NbtFloat)
            ?: doubleOrNull?.let(::NbtDouble)
            ?: booleanOrNull?.let(::NbtByte)
            ?: unreachable)

    JsonNull -> unreachable
}

internal fun List<NbtTag>.asNbtListUnsafe(): NbtList<NbtTag> {
    val first = first()

    @Suppress(
        "CAST_NEVER_SUCCEEDS", "UNCHECKED_CAST",
        "UPPER_BOUND_VIOLATED_IN_TYPE_OPERATOR_OR_PARAMETER_BOUNDS_WARNING"
    )
    fun <T> cast(): List<T> = this as List<T>
    return when (first) {
        is NbtByte -> NbtList(cast<NbtByte>())
        is NbtByteArray -> NbtList(cast<NbtByteArray>())
        is NbtCompound -> NbtList(cast<NbtCompound>())
        is NbtDouble -> NbtList(cast<NbtDouble>())
        is NbtFloat -> NbtList(cast<NbtFloat>())
        is NbtInt -> NbtList(cast<NbtInt>())
        is NbtIntArray -> NbtList(cast<NbtIntArray>())
        is NbtLong -> NbtList(cast<NbtLong>())
        is NbtLongArray -> NbtList(cast<NbtLongArray>())
        is NbtShort -> NbtList(cast<NbtShort>())
        is NbtString -> NbtList(cast<NbtString>())
        is NbtList<*> -> NbtList(cast<NbtList<*>>())
    }
}
