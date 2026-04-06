@file:OptIn(InternalSerializationApi::class)

package mct.text

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import mct.serializer.toNbtListUnsafe
import mct.util.putIfPresent
import net.benwoodworth.knbt.*

class TextCompoundSerializer : KSerializer<TextCompound> {
    override val descriptor: SerialDescriptor =
        buildSerialDescriptor("mct.text.TextCompound", SerialKind.CONTEXTUAL) // dynamic

    override fun serialize(encoder: Encoder, value: TextCompound) = when (encoder) {
        is JsonEncoder -> serializeJson(encoder, value)
        is NbtEncoder -> serializeNbt(encoder, value)
        else -> error("Unsupported decoder")
    }

    private fun serializeJson(encoder: JsonEncoder, value: TextCompound) {
        val json = encoder.json

        fun simplifyRecursively(value: TextCompound): JsonElement {
            fun List<TextCompound>.simplify(vararg prefixes: JsonElement, remainList: Boolean = false): JsonElement? {
                val compounds = this
                return if (compounds.isEmpty()) JsonArray(prefixes.asList()) else {
                    compounds.fold(prefixes.toMutableList()) { acc, compound ->
                        when (val r = simplifyRecursively(compound)) {
                            is JsonArray -> acc.addAll(r)
                            else -> acc.add(r)
                        }
                        acc
                    }
                }.let {
                    when (it.size) {
                        0 -> null
                        1 if !remainList -> it.first()
                        else -> JsonArray(it)
                    }
                }
            }

            fun JsonObjectBuilder.commonPut() {
                putIfPresent("extra", value.extra.simplify())
                putIfPresent("color", value.color)
                putIfPresent("bold", value.bold)
                putIfPresent("italic", value.italic)
                putIfPresent("underlined", value.underlined)
                putIfPresent("strikethrough", value.strikethrough)
                putIfPresent("obfuscated", value.obfuscated)
            }
            return when (value) {
                is TextCompound.Plain -> {
                    if (value.isPlainText()) {
                        val self = JsonPrimitive(value.text)
                        if (value.extra.isEmpty()) self else {
                            value.extra.simplify(self)!!
                        }
                    } else {
                        buildJsonObject {
                            put("text", value.text)
                            commonPut()
                        }

                    }
                }

                is TextCompound.Translatable -> {
                    buildJsonObject {
                        put("translate", value.translate)
                        putIfPresent("fallback", value.fallback)
                        putIfPresent("with", value.with.simplify(remainList = true))
                        commonPut()
                    }
                }

                else -> json.compoundEncodeToJsonElement(value)

            }
        }

        val simplified = simplifyRecursively(value)
        encoder.encodeJsonElement(simplified)
    }

    private fun serializeNbt(encoder: NbtEncoder, value: TextCompound) {
        val nbt = encoder.nbt

        fun simplifyRecursively(value: TextCompound): NbtTag {
            fun List<TextCompound>.simplify(vararg prefixes: NbtTag, remainList: Boolean = false): NbtTag? {
                val compounds = this
                return if (compounds.isEmpty()) prefixes.asList().toNbtListUnsafe() else {
                    compounds.fold(prefixes.toMutableList()) { acc, compound ->
                        when (val r = simplifyRecursively(compound)) {
                            is NbtList<*> -> acc.addAll(r)
                            else -> acc.add(r)
                        }
                        acc
                    }
                }.let {
                    when (it.size) {
                        0 -> null
                        1 if !remainList -> it.first()
                        else -> it.toNbtListUnsafe()
                    }
                }
            }

            fun NbtCompoundBuilder.commonPut() {
                putIfPresent("extra", value.extra.simplify())
                putIfPresent("color", value.color)
                putIfPresent("bold", value.bold)
                putIfPresent("italic", value.italic)
                putIfPresent("underlined", value.underlined)
                putIfPresent("strikethrough", value.strikethrough)
                putIfPresent("obfuscated", value.obfuscated)
            }

            return when (value) {
                is TextCompound.Plain -> {
                    if (value.isPlainText()) {
                        val self = NbtString(value.text)
                        if (value.extra.isEmpty()) self else {
                            value.extra.simplify(self)!!
                        }
                    } else {
                        buildNbtCompound {
                            put("text", value.text)
                            commonPut()
                        }
                    }
                }

                is TextCompound.Translatable -> {
                    buildNbtCompound {
                        put("translate", value.translate)
                        putIfPresent("fallback", value.fallback)
                        putIfPresent("with", value.with.simplify(remainList = true))
                        commonPut()
                    }
                }

                else -> nbt.compoundEncodeToNbtTag(value)
            }
        }

        val simplified = simplifyRecursively(value)
        encoder.encodeNbtTag(simplified) // FIXME: Here knbt will wrap it in a compound again
    }


    override fun deserialize(decoder: Decoder): TextCompound = when (decoder) {
        is JsonDecoder -> deserializeJson(decoder)
        is NbtDecoder -> deserializeNbt(decoder)
        else -> error("Unsupported decoder")
    }


    private fun deserializeJson(decoder: JsonDecoder): TextCompound {
        val json = decoder.json

        fun deserializeRecursively(element: JsonElement): TextCompound = when (element) {
            is JsonPrimitive -> if (element.isString) {
                TextCompound.Plain(element.content)
            } else {
                error("Illegal JSON element $element, which isn't a string")
            }

            is JsonArray -> element.map(::deserializeRecursively).flatMap(TextCompound::flatten).multi()

            is JsonObject -> when {
                "type" in element -> json.decodeFromJsonElement<TextCompound>(element)
                "text" in element -> json.decodeFromJsonElement<TextCompound.Plain>(element)
                "translate" in element -> json.decodeFromJsonElement<TextCompound.Translatable>(element)
                "keybind" in element -> json.decodeFromJsonElement<TextCompound.Keybind>(element)
                "score" in element -> json.decodeFromJsonElement<TextCompound.Score>(element)
                "selector" in element -> json.decodeFromJsonElement<TextCompound.Selector>(element)
                "object" in element -> json.decodeFromJsonElement<TextCompound.Object>(element)
                else -> error("Isn't a TextCompound")
            }

            JsonNull -> error("Illegal JSON element null")
        }


        return deserializeRecursively(decoder.decodeJsonElement())
    }

    private fun deserializeNbt(decoder: NbtDecoder): TextCompound {
        val nbt = decoder.nbt

        fun deserializeRecursively(element: NbtTag): TextCompound = when (element) {
            is NbtString -> TextCompound.Plain(element.value)

            is NbtList<NbtTag> -> element.map(::deserializeRecursively).flatMap(TextCompound::flatten).multi()

            is NbtCompound -> when {
                "type" in element -> nbt.decodeFromNbtTag<TextCompound>(element)
                "text" in element -> nbt.decodeFromNbtTag<TextCompound.Plain>(element)
                "translatable" in element -> nbt.decodeFromNbtTag<TextCompound.Translatable>(element)
                "keybind" in element -> nbt.decodeFromNbtTag<TextCompound.Keybind>(element)
                "score" in element -> nbt.decodeFromNbtTag<TextCompound.Score>(element)
                "selector" in element -> nbt.decodeFromNbtTag<TextCompound.Selector>(element)
                "object" in element -> nbt.decodeFromNbtTag<TextCompound.Object>(element)
                else -> error("Isn't a TextCompound")
            }

            else -> error("Illegal NBT element $element")
        }

        return deserializeRecursively(decoder.decodeNbtTag())
    }
}

private fun TextCompound.flatten(): List<TextCompound> = when (val compound = this) {
    is TextCompound.Plain if compound.isPlainText() && compound.extra.isNotEmpty() -> listOf(compound.copy(extra = emptyList())) +
            compound.extra.flatMap(TextCompound::flatten)

    is TextCompound.Translatable if compound.isPlainText() && compound.extra.isNotEmpty() -> listOf(compound.copy(extra = emptyList())) +
            compound.extra.flatMap(TextCompound::flatten)
    else -> listOf(compound)
}

private fun Json.compoundEncodeToJsonElement(
    value: TextCompound,
): JsonElement = when (value) {
    is TextCompound.Plain ->
        encodeToJsonElement(TextCompound.Plain.serializer(), value)

    is TextCompound.Translatable ->
        encodeToJsonElement(TextCompound.Translatable.serializer(), value)

    is TextCompound.Keybind ->
        encodeToJsonElement(TextCompound.Keybind.serializer(), value)

    is TextCompound.Score ->
        encodeToJsonElement(TextCompound.Score.serializer(), value)

    is TextCompound.Selector ->
        encodeToJsonElement(TextCompound.Selector.serializer(), value)

    is TextCompound.Nbt ->
        encodeToJsonElement(TextCompound.Nbt.serializer(), value)

    is TextCompound.Object ->
        encodeToJsonElement(TextCompound.Object.serializer(), value)
}

private fun NbtFormat.compoundEncodeToNbtTag(
    value: TextCompound,
): NbtTag = when (value) {
    is TextCompound.Plain ->
        encodeToNbtTag(TextCompound.Plain.serializer(), value)

    is TextCompound.Translatable ->
        encodeToNbtTag(TextCompound.Translatable.serializer(), value)

    is TextCompound.Keybind ->
        encodeToNbtTag(TextCompound.Keybind.serializer(), value)

    is TextCompound.Score ->
        encodeToNbtTag(TextCompound.Score.serializer(), value)

    is TextCompound.Selector ->
        encodeToNbtTag(TextCompound.Selector.serializer(), value)

    is TextCompound.Nbt ->
        encodeToNbtTag(TextCompound.Nbt.serializer(), value)

    is TextCompound.Object ->
        encodeToNbtTag(TextCompound.Object.serializer(), value)
}

