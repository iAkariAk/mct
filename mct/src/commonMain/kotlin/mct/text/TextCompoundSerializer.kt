package mct.text

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import mct.serializer.toNbtListUnsafe
import net.benwoodworth.knbt.*

class TextCompoundSerializer : KSerializer<TextCompound> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("mct.text.TextCompound") // dynamic

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
                value.color?.let { put("color", it) }
                value.bold?.let { put("bold", it) }
                value.italic?.let { put("italic", it) }
                value.underlined?.let { put("underlined", it) }
                value.strikethrough?.let { put("strikethrough", it) }
                value.obfuscated?.let { put("obfuscated", it) }
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
                            value.extra.simplify()?.let { put("extra", it) }
                        }
                    }
                }

                is TextCompound.Translatable -> {
                    buildJsonObject {
                        put("translate", value.translate)
                        value.fallback?.let { put("fallback", it) }
                        commonPut()
                        value.extra.simplify()?.let { put("extra", it) }
                        value.with.simplify(remainList = true)?.let { put("with", it) }
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
                    value.color?.let { put("color", it) }
                    value.bold?.let { put("bold", it) }
                    value.italic?.let { put("italic", it) }
                    value.underlined?.let { put("underlined", it) }
                    value.strikethrough?.let { put("strikethrough", it) }
                    value.obfuscated?.let { put("obfuscated", it) }
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
                                value.extra.simplify()?.let { put("extra", it) }
                            }
                        }
                    }

                    is TextCompound.Translatable -> {
                        buildNbtCompound {
                            put("translate", value.translate)
                            value.fallback?.let { put("fallback", it) }
                            commonPut()
                            value.extra.simplify()?.let { put("extra", it) }
                            value.with.simplify(remainList = true)?.let { put("with", it) }
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

            is JsonArray -> element.map(::deserializeRecursively).multi()

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

            is NbtList<NbtTag> -> element.map(::deserializeRecursively).multi()

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

