package mct.serializer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.IntArraySerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object IntRangeSerializer : KSerializer<IntRange> {
    private val delegated = IntArraySerializer()
    override val descriptor = SerialDescriptor("mct.serializer.IntRangeSerializer", delegated.descriptor)

    override fun serialize(encoder: Encoder, value: IntRange) =
        delegated.serialize(encoder, intArrayOf(value.first, value.last))

    override fun deserialize(decoder: Decoder): IntRange =
        delegated.deserialize(decoder).let { (startInclusive, endInclusive) -> IntRange(startInclusive, endInclusive) }
}

typealias IntRangeSerializable = @Serializable(IntRangeSerializer::class) IntRange