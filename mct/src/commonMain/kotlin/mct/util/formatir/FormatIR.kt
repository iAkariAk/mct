package mct.util.formatir

import kotlinx.serialization.json.*
import kotlinx.serialization.json.JsonNull.isString
import mct.serializer.toNbtListUnsafe
import net.benwoodworth.knbt.*

sealed interface IRConverter<T> {
    fun encodeToIR(element: T): IRElement
    fun decodeFromIR(element: IRElement): T
}

sealed interface IRElement
data class IRByte(val value: Byte) : IRElement
data class IRShort(val value: Short) : IRElement
data class IRInt(val value: Int) : IRElement
data class IRLong(val value: Long) : IRElement
data class IRFloat(val value: Float) : IRElement
data class IRDouble(val value: Double) : IRElement
data class IRBoolean(val value: Boolean) : IRElement
data class IRString(val value: String) : IRElement
data class IRObject(val value: Map<String, IRElement>) : IRElement, Map<String, IRElement> by value
data class IRList(val value: List<IRElement>) : IRElement, List<IRElement> by value
data object IRNull : IRElement

fun IRElement.toJson() = JsonIRConverter.decodeFromIR(this)
fun JsonElement.toIR() = JsonIRConverter.encodeToIR(this)
fun IRElement.toNbt() = NbtTagIRConverter.decodeFromIR(this)
fun NbtTag.toIR() = NbtTagIRConverter.encodeToIR(this)

object JsonIRConverter : IRConverter<JsonElement> {
    override fun encodeToIR(element: JsonElement): IRElement = when (element) {
        is JsonArray -> IRList(element.map(::encodeToIR))
        is JsonObject -> IRObject(element.mapValues { encodeToIR(it.value) })
        JsonNull -> IRNull // JsonNull is subtype of JsonPrimitive, thus which should be above next
        is JsonPrimitive -> {
            if (isString) return IRString(element.content)
            element.longOrNull?.let(::IRLong) ?: element.intOrNull?.let(::IRInt)
            ?: element.doubleOrNull?.let(::IRDouble) ?: element.floatOrNull?.let(::IRFloat)
            ?: element.booleanOrNull?.let(::IRBoolean) ?: element.contentOrNull?.let(::IRString)!!
        }
    }

    override fun decodeFromIR(element: IRElement): JsonElement = when (element) {
        is IRBoolean -> JsonPrimitive(element.value)
        is IRDouble -> JsonPrimitive(element.value)
        is IRFloat -> JsonPrimitive(element.value)
        is IRByte -> JsonPrimitive(element.value)
        is IRShort -> JsonPrimitive(element.value)
        is IRInt -> JsonPrimitive(element.value)
        is IRLong -> JsonPrimitive(element.value)
        is IRString -> JsonPrimitive(element.value)
        is IRObject -> JsonObject(element.value.mapValues { decodeFromIR(it.value) })
        is IRList -> JsonArray(element.value.map(::decodeFromIR))
        IRNull -> JsonNull
    }
}

object NbtTagIRConverter : IRConverter<NbtTag> {
    override fun encodeToIR(element: NbtTag): IRElement = when (element) {
        is NbtByte -> IRByte(element.value)
        is NbtShort -> IRShort(element.value)
        is NbtInt -> IRInt(element.value)
        is NbtLong -> IRLong(element.value)
        is NbtFloat -> IRFloat(element.value)
        is NbtDouble -> IRDouble(element.value)
        is NbtString -> IRString(element.value)
        is NbtList<*> -> IRList(element.map(::encodeToIR))
        is NbtCompound -> IRObject(element.mapValues { encodeToIR(it.value) })
        is NbtLongArray -> IRList(element.map(::IRLong))
        is NbtByteArray -> IRList(element.map(::IRByte))
        is NbtIntArray -> IRList(element.map(::IRInt))
    }

    override fun decodeFromIR(element: IRElement): NbtTag = when (element) {
        is IRBoolean -> NbtByte(if (element.value) 1 else 0)
        is IRFloat -> NbtFloat(element.value)
        is IRDouble -> NbtDouble(element.value)
        is IRByte -> NbtByte(element.value)
        is IRShort -> NbtShort(element.value)
        is IRInt -> NbtInt(element.value)
        is IRLong -> NbtLong(element.value)
        is IRString -> NbtString(element.value)
        is IRList -> element.value.map(::decodeFromIR).toNbtListUnsafe()
        is IRObject -> NbtCompound(element.value.mapValues { decodeFromIR(it.value) })
        IRNull -> error("null is not allowed in NBT")
    }
}