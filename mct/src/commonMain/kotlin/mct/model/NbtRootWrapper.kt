@file:OptIn(ExperimentalSerializationApi::class)

package mct.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import net.benwoodworth.knbt.*
import kotlin.jvm.JvmInline

@Serializable(NbtRootWrapperSerializer::class)
@JvmInline
value class NbtRootWrapper<T>(
    val value: T,
)

private val NBT_CACHES = mutableMapOf<NbtFormat, NbtFormat>()
private fun NbtFormat.removeRootSerializer(): NbtFormat = when (this) {
    is Nbt -> Nbt(this) {
        nameRootClasses = false
    }

    is StringifiedNbt -> StringifiedNbt(this) {
        nameRootClasses = false
    }
}

class NbtRootWrapperSerializer<T>(
    private val valueSerializer: KSerializer<T>
) : KSerializer<NbtRootWrapper<T>> {
    override val descriptor = buildSerialDescriptor("mct.model.NbtRootWrapper", PolymorphicKind.SEALED) {
        element("Wrapped", NbtCompound.serializer().descriptor)
        element("Nonwrapped", valueSerializer.descriptor)
    }
    private val rootName = valueSerializer.descriptor.serialName

    override fun deserialize(decoder: Decoder): NbtRootWrapper<T> {
        val value = when (decoder) {
            is NbtDecoder -> {
                val tag = decoder.decodeNbtTag()
                val unwrapped = (tag as? NbtCompound)?.values?.singleOrNull() ?: tag
                val nbt = NBT_CACHES.getOrPut(decoder.nbt) { decoder.nbt.removeRootSerializer() }
                nbt.decodeFromNbtTag(valueSerializer, unwrapped)
            }

            is JsonDecoder -> {
                val tag = decoder.decodeJsonElement()
                val unwrapped = (tag as? JsonObject)?.values?.singleOrNull() ?: tag
                decoder.json.decodeFromJsonElement(valueSerializer, unwrapped)
            }

            else -> error("Unsupported decoder $decoder")
        }
        return NbtRootWrapper(value)

    }

    override fun serialize(encoder: Encoder, value: NbtRootWrapper<T>) {
        when (encoder) {
            is NbtEncoder -> {
                val wrapped = buildNbtCompound {
                    val value = encoder.nbt.encodeToNbtTag(valueSerializer, value.value)
                    put(rootName, value)
                }
                encoder.encodeNbtTag(wrapped)
            }

            is JsonEncoder -> {
                val wrapped = buildJsonObject {
                    val value = encoder.json.encodeToJsonElement(valueSerializer, value.value)
                    put(rootName, value)
                }
                encoder.encodeJsonElement(wrapped)
            }

            else -> error("Unsupported encoder $encoder")
        }
    }
}