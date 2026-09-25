@file:OptIn(ExperimentalSerializationApi::class)

package mct.model.text

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import mct.util.formatir.IRElement

object TextComponentSerializer : KSerializer<TextComponent<*>> {
    private val irSerializer = IRElement.serializer()

    override val descriptor = SerialDescriptor("mct.model.text.TextComponentSerializer", irSerializer.descriptor)

    override fun serialize(encoder: Encoder, value: TextComponent<*>) =
        irSerializer.serialize(encoder, value.toIR())


    override fun deserialize(decoder: Decoder): TextComponent<*> =
        irSerializer.deserialize(decoder).decodeToCompound()
}