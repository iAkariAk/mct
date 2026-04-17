@file:OptIn(InternalSerializationApi::class)

package mct.text

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import mct.util.formatir.toIR
import mct.util.formatir.toJson
import mct.util.formatir.toNbt
import net.benwoodworth.knbt.NbtDecoder
import net.benwoodworth.knbt.NbtEncoder

class TextCompoundSerializer : KSerializer<TextCompound> {
    override val descriptor: SerialDescriptor =
        buildSerialDescriptor("mct.text.TextCompound", SerialKind.CONTEXTUAL) // dynamic

    override fun serialize(encoder: Encoder, value: TextCompound) = when (encoder) {
        is JsonEncoder -> serializeJson(encoder, value)
        is NbtEncoder -> serializeNbt(encoder, value)
        else -> error("Unsupported decoder")
    }

    private fun serializeJson(encoder: JsonEncoder, value: TextCompound) {
        val simplified = value.encodeToIR(true).toJson()
        encoder.encodeJsonElement(simplified)
    }

    private fun serializeNbt(encoder: NbtEncoder, value: TextCompound) {
        val simplified = value.encodeToIR(true).toNbt()
        encoder.encodeNbtTag(simplified)
    }


    override fun deserialize(decoder: Decoder): TextCompound = when (decoder) {
        is JsonDecoder -> deserializeJson(decoder)
        is NbtDecoder -> deserializeNbt(decoder)
        else -> error("Unsupported decoder")
    }


    private fun deserializeJson(decoder: JsonDecoder): TextCompound = decoder.decodeJsonElement().toIR().decodeToCompound()

    private fun deserializeNbt(decoder: NbtDecoder): TextCompound = decoder.decodeNbtTag().toIR().decodeToCompound()
}


