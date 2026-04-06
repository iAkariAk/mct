package mct.util

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import net.benwoodworth.knbt.*

inline fun JsonObjectBuilder.putIfPresent(key: String, value: JsonElement?): JsonElement? =
    value?.let { put(key, value) }

inline fun JsonObjectBuilder.putIfPresent(key: String, value: Boolean?): JsonElement? =
    putIfPresent(key, value?.let(::JsonPrimitive))

inline fun JsonObjectBuilder.putIfPresent(key: String, value: Number?): JsonElement? =
    putIfPresent(key, value?.let(::JsonPrimitive))

inline fun JsonObjectBuilder.putIfPresent(key: String, value: String?): JsonElement? =
    putIfPresent(key, value?.let(::JsonPrimitive))

@Suppress("UNUSED_PARAMETER") // allows to call `put("key", null)`
inline fun JsonObjectBuilder.putIfPresent(key: String, value: Nothing?): JsonElement? = putIfPresent(key, JsonPrimitive(value))

inline fun NbtCompoundBuilder.putIfPresent(key: String, value: NbtTag?): NbtTag? = value?.let { put(key, it) }

inline fun NbtCompoundBuilder.putIfPresent(key: String, value: Byte?): NbtTag? = putIfPresent(key, value?.let(::NbtByte))
inline fun NbtCompoundBuilder.putIfPresent(key: String, value: Boolean?): NbtTag? = putIfPresent(key, value?.let(::NbtByte))
inline fun NbtCompoundBuilder.putIfPresent(key: String, value: Short?): NbtTag? = putIfPresent(key, value?.let(::NbtShort))
inline fun NbtCompoundBuilder.putIfPresent(key: String, value: Int?): NbtTag? = putIfPresent(key, value?.let(::NbtInt))
inline fun NbtCompoundBuilder.putIfPresent(key: String, value: Long?): NbtTag? = putIfPresent(key, value?.let(::NbtLong))
inline fun NbtCompoundBuilder.putIfPresent(key: String, value: Float?): NbtTag? = putIfPresent(key, value?.let(::NbtFloat))
inline fun NbtCompoundBuilder.putIfPresent(key: String, value: Double?): NbtTag? = putIfPresent(key, value?.let(::NbtDouble))
inline fun NbtCompoundBuilder.putIfPresent(key: String, value: String?): NbtTag? = putIfPresent(key, value?.let(::NbtString))

inline fun NbtCompoundBuilder.putIfPresent(key: String, value: ByteArray?): NbtTag? =
    putIfPresent(key, value?.let(::NbtByteArray))

inline fun NbtCompoundBuilder.putIfPresent(key: String, value: IntArray?): NbtTag? =
    putIfPresent(key, value?.let(::NbtIntArray))

inline fun NbtCompoundBuilder.putIfPresent(key: String, value: LongArray?): NbtTag? =
    putIfPresent(key, value?.let(::NbtLongArray))
