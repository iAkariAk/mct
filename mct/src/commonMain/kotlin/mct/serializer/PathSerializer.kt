package mct.serializer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import okio.Path
import okio.Path.Companion.toPath

typealias PathSerializable = @Serializable(PathSerializer::class) Path

object PathSerializer : KSerializer<Path> {
    override val descriptor = PrimitiveSerialDescriptor("okio.Path", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): Path =
        decoder.decodeString().toPath()


    override fun serialize(encoder: Encoder, value: Path) =
        encoder.encodeString(value.toString())
}