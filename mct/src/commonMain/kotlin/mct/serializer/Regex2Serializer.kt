package mct.serializer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import mct.util.Regex2

object Regex2Serializer : KSerializer<Regex2> {
    override val descriptor = buildSerialDescriptor("mct.serializer.Regex2Serializer", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Regex2) = encoder.encodeString(value.pattern)
    override fun deserialize(decoder: Decoder): Regex2 = Regex2(decoder.decodeString())
}

typealias Regex2Serializable = @Serializable(Regex2Serializer::class) Regex2