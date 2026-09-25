@file:OptIn(ExperimentalSerializationApi::class)

package mct.util.formatir

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import mct.util.deferDescriptor
import net.benwoodworth.knbt.NbtDecoder

object IRElementSerializer : KSerializer<IRElement> {
    override val descriptor = buildSerialDescriptor("mct.util.formatir.IRElementSerializer", PolymorphicKind.SEALED) {
        element("IRList", deferDescriptor { IRListSerializer.descriptor })
        element("IRObject", deferDescriptor { IRObjectSerializer.descriptor })
        element("IRBoolean", IRBooleanSerializer.descriptor)
        element("IRString", IRStringSerializer.descriptor)
        element("IRByte", IRByteSerializer.descriptor)
        element("IRShort", IRShortSerializer.descriptor)
        element("IRInt", IRIntSerializer.descriptor)
        element("IRLong", IRLongSerializer.descriptor)
        element("IRFloat", IRFloatSerializer.descriptor)
        element("IRDouble", IRDoubleSerializer.descriptor)
    }

    override fun serialize(encoder: Encoder, value: IRElement) {
        when (value) {
            IRNull -> encoder.encodeNull()
            is IRList -> IRListSerializer.serialize(encoder, value)
            is IRObject -> IRObjectSerializer.serialize(encoder, value)
            is IRBoolean -> encoder.encodeBoolean(value.value)
            is IRString -> encoder.encodeString(value.value)
            is IRByte -> encoder.encodeByte(value.value)
            is IRShort -> encoder.encodeShort(value.value)
            is IRInt -> encoder.encodeInt(value.value)
            is IRLong -> encoder.encodeLong(value.value)
            is IRFloat -> encoder.encodeFloat(value.value)
            is IRDouble -> encoder.encodeDouble(value.value)

        }
    }

    override fun deserialize(decoder: Decoder): IRElement = when (decoder) {
        is JsonDecoder -> {
            val element = decoder.decodeJsonElement()
            JsonIRConverter.encodeToIR(element)
        }

        is NbtDecoder -> {
            val tag = decoder.decodeNbtTag()
            NbtTagIRConverter.encodeToIR(tag)
        }

        else -> error("Unsupported decoder $decoder")
    }
}

object IRListSerializer : KSerializer<IRList> {
    private val delegated = ListSerializer(IRElementSerializer)
    override val descriptor = SerialDescriptor("mct.util.formatir.IRListSerializer", delegated.descriptor)

    override fun serialize(encoder: Encoder, value: IRList) = delegated.serialize(encoder, value.value)

    override fun deserialize(decoder: Decoder): IRList = IRList(delegated.deserialize(decoder))
}

object IRObjectSerializer : KSerializer<IRObject> {
    private val delegated = MapSerializer(String.serializer(), IRElementSerializer)
    override val descriptor = SerialDescriptor("mct.util.formatir.IRListSerializer", delegated.descriptor)

    override fun serialize(encoder: Encoder, value: IRObject) = delegated.serialize(encoder, value.value)

    override fun deserialize(decoder: Decoder): IRObject = IRObject(delegated.deserialize(decoder))
}

object IRByteSerializer : KSerializer<IRByte> {
    override val descriptor = PrimitiveSerialDescriptor("mct.util.formatir.IRByteSerializer", PrimitiveKind.BYTE)

    override fun serialize(encoder: Encoder, value: IRByte) = encoder.encodeByte(value.value)

    override fun deserialize(decoder: Decoder): IRByte = IRByte(decoder.decodeByte())
}

object IRShortSerializer : KSerializer<IRShort> {
    override val descriptor = PrimitiveSerialDescriptor("mct.util.formatir.IRShortSerializer", PrimitiveKind.SHORT)

    override fun serialize(encoder: Encoder, value: IRShort) = encoder.encodeShort(value.value)

    override fun deserialize(decoder: Decoder): IRShort = IRShort(decoder.decodeShort())
}

object IRIntSerializer : KSerializer<IRInt> {
    override val descriptor = PrimitiveSerialDescriptor("mct.util.formatir.IRIntSerializer", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: IRInt) = encoder.encodeInt(value.value)

    override fun deserialize(decoder: Decoder): IRInt = IRInt(decoder.decodeInt())
}

object IRLongSerializer : KSerializer<IRLong> {
    override val descriptor = PrimitiveSerialDescriptor("mct.util.formatir.IRLongSerializer", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: IRLong) = encoder.encodeLong(value.value)

    override fun deserialize(decoder: Decoder): IRLong = IRLong(decoder.decodeLong())
}

object IRFloatSerializer : KSerializer<IRFloat> {
    override val descriptor = PrimitiveSerialDescriptor("mct.util.formatir.IRFloatSerializer", PrimitiveKind.FLOAT)

    override fun serialize(encoder: Encoder, value: IRFloat) = encoder.encodeFloat(value.value)

    override fun deserialize(decoder: Decoder): IRFloat = IRFloat(decoder.decodeFloat())
}

object IRDoubleSerializer : KSerializer<IRDouble> {
    override val descriptor = PrimitiveSerialDescriptor("mct.util.formatir.IRDoubleSerializer", PrimitiveKind.DOUBLE)

    override fun serialize(encoder: Encoder, value: IRDouble) = encoder.encodeDouble(value.value)

    override fun deserialize(decoder: Decoder): IRDouble = IRDouble(decoder.decodeDouble())
}

object IRBooleanSerializer : KSerializer<IRBoolean> {
    override val descriptor = PrimitiveSerialDescriptor("mct.util.formatir.IRBooleanSerializer", PrimitiveKind.BOOLEAN)

    override fun serialize(encoder: Encoder, value: IRBoolean) = encoder.encodeBoolean(value.value)

    override fun deserialize(decoder: Decoder): IRBoolean = IRBoolean(decoder.decodeBoolean())
}

object IRStringSerializer : KSerializer<IRString> {
    override val descriptor = PrimitiveSerialDescriptor("mct.util.formatir.IRStringSerializer", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: IRString) = encoder.encodeString(value.value)

    override fun deserialize(decoder: Decoder): IRString = IRString(decoder.decodeString())
}