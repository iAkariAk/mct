package mct.text

import mct.util.formatir.*

private fun TextCompound.flatten(): List<TextCompound> = when (val compound = this) {
    is TextCompound.Plain if compound.isPlainStyle() && compound.extra.isNotEmpty() -> listOf(compound.copy(extra = emptyList())) +
            compound.extra.flatMap(TextCompound::flatten)

    is TextCompound.Translatable if compound.isPlainStyle() && compound.extra.isNotEmpty() -> listOf(compound.copy(extra = emptyList())) +
            compound.extra.flatMap(TextCompound::flatten)

    else -> listOf(compound)
}


fun IRElement.decodeToCompound(): TextCompound = when (this) {
    is IRString -> TextCompound.Plain(value)
    is IRList -> map { it.decodeToCompound() }.flatMap(TextCompound::flatten).multi()
    is IRObject -> decodeToCompound()

    else -> error("Illegal element type")
}


private fun IRObject.decodeToCompound(): TextCompound = when {
    containsKey("type") -> when (val type = (this["type"] as? IRString)?.value) {
        "text" -> decodeAsPlain()
        "translate" -> decodeAsTranslatable()
        "keybind" -> decodeAsKeybind()
        "score" -> decodeAsScore()
        "selector" -> decodeAsSelector()
        "nbt" -> decodeAsNbt()
        "object" -> decodeAsObject()
        "sprite" -> decodeAsSprite()
        null -> error("Type must be string")
        else -> error("Unsupported type $type")
    }

    containsKey("text") -> decodeAsPlain()
    containsKey("translate") -> decodeAsTranslatable()
    containsKey("keybind") -> decodeAsKeybind()
    containsKey("score") -> decodeAsScore()
    containsKey("selector") -> decodeAsSelector()
    containsKey("nbt") -> decodeAsNbt()
    containsKey("object") -> decodeAsObject()
    containsKey("sprite") -> decodeAsSprite()
    else -> error("Unknown TextCompound type: $this")
}

private fun IRObject.decodeCommon() = object {
    val extra = run {
        val obj = this@decodeCommon
        val extra = when (val extra = obj["extra"]) {
            is IRObject -> IRList(listOf(extra))
            is IRString -> IRList(listOf(extra))
            is IRList -> extra
            else -> null
        }
        extra?.value?.map { it.decodeToCompound() } ?: emptyList()
    }
    val color = optional<IRString>("color")?.value
    val bold = optionalBoolean("bold")
    val italic = optionalBoolean("italic")
    val underlined = optionalBoolean("underlined")
    val strikethrough = optionalBoolean("strikethrough")
    val obfuscated = optionalBoolean("obfuscated")
}

private fun IRObject.decodeAsPlain() = decodeCommon().let {
    TextCompound.Plain(
        text = when (val t = this["text"] as? IRString) {
            is IRString -> t.value
            else -> it.toString()
        },
        extra = it.extra,
        color = it.color,
        bold = it.bold,
        italic = it.italic,
        underlined = it.underlined,
        strikethrough = it.strikethrough,
        obfuscated = it.obfuscated,
    )
}

private fun IRObject.decodeAsTranslatable() = decodeCommon().let {
    TextCompound.Translatable(
        translate = require<IRString>("translate").value,
        fallback = optional<IRString>("fallback")?.value,
        with = optional<IRList>("with")?.value?.map { it.decodeToCompound() } ?: emptyList(),
        extra = it.extra,
        color = it.color,
        bold = it.bold,
        italic = it.italic,
        underlined = it.underlined,
        strikethrough = it.strikethrough,
        obfuscated = it.obfuscated,
    )
}

private fun IRObject.decodeAsKeybind() = decodeCommon().let {
    TextCompound.Keybind(
        keybind = require<IRString>("keybind").value,
        extra = it.extra,
        color = it.color,
        bold = it.bold,
        italic = it.italic,
        underlined = it.underlined,
        strikethrough = it.strikethrough,
        obfuscated = it.obfuscated,
    )
}

private fun IRObject.decodeAsScore() = decodeCommon().let {
    val scoreObj = require<IRObject>("score")
    TextCompound.Score(
        score = TextCompound.Score.Info(
            name = scoreObj.require<IRString>("name").value,
            objective = scoreObj.require<IRString>("objective").value,
        ),
        extra = it.extra,
        color = it.color,
        bold = it.bold,
        italic = it.italic,
        underlined = it.underlined,
        strikethrough = it.strikethrough,
        obfuscated = it.obfuscated,
    )
}

private fun IRObject.decodeAsSelector() = decodeCommon().let {
    TextCompound.Selector(
        selector = require<IRString>("selector").value,
        separator = optional<IRElement>("separator")?.decodeToCompound(),
        extra = it.extra,
        color = it.color,
        bold = it.bold,
        italic = it.italic,
        underlined = it.underlined,
        strikethrough = it.strikethrough,
        obfuscated = it.obfuscated,
    )
}

private fun IRObject.decodeAsNbt() = decodeCommon().let {
    TextCompound.Nbt(
        nbt = require<IRString>("nbt").value,
        interpret = optionalBoolean("interpret") ?: false,
        separator = optional<IRElement>("separator")?.decodeToCompound(),
        entity = optional<IRString>("entity")?.value,
        block = optional<IRString>("block")?.value,
        storage = optional<IRString>("storage")?.value,
        extra = it.extra,
        color = it.color,
        bold = it.bold,
        italic = it.italic,
        underlined = it.underlined,
        strikethrough = it.strikethrough,
        obfuscated = it.obfuscated,
    )
}

private fun IRObject.decodeAsObject() = decodeCommon().let {
    TextCompound.Object(
        `object` = require<IRString>("object").value,
        fallback = optional<IRString>("fallback")?.value,
        extra = it.extra,
        color = it.color,
        bold = it.bold,
        italic = it.italic,
        underlined = it.underlined,
        strikethrough = it.strikethrough,
        obfuscated = it.obfuscated,
    )
}

private fun IRObject.decodeAsSprite() = decodeCommon().let {
    TextCompound.Sprite(
        sprite = require<IRString>("sprite").value,
        extra = it.extra,
        color = it.color,
        bold = it.bold,
        italic = it.italic,
        underlined = it.underlined,
        strikethrough = it.strikethrough,
        obfuscated = it.obfuscated,
    )
}



private inline fun <reified T : IRElement> IRElement.requireTypeOf(field: String) =
    this as? T ?: error("Expected ${T::class} but found ${this::class} in $field")

private inline fun <reified T : IRElement> IRObject.require(key: String): T =
    this[key]?.requireTypeOf<T>(key) ?: error("$key not found")

private inline fun <reified T : IRElement> IRObject.optional(key: String): T? =
    this[key]?.requireTypeOf<T>(key)

private inline fun IRObject.optionalBoolean(key: String): Boolean? =
    when (val v = this[key]) {
        null -> null
        is IRBoolean -> v.value
        is IRByte -> v.value == 1.toByte()
        else -> error("Expected boolean(byte) but found ${this::class} in $key")
    }