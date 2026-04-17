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
    else -> error("Unknown TextCompound type: $this")
}

private fun IRObject.decodeCommon() = object {
    val extra = optional<IRList>("extra")?.value?.map { it.decodeToCompound() } ?: emptyList()
    val color = optional<IRString>("color")?.value
    val bold = optional<IRBoolean>("bold")?.value
    val italic = optional<IRBoolean>("italic")?.value
    val underlined = optional<IRBoolean>("underlined")?.value
    val strikethrough = optional<IRBoolean>("strikethrough")?.value
    val obfuscated = optional<IRBoolean>("obfuscated")?.value
}

private fun IRObject.decodeAsPlain() = decodeCommon().let {
    TextCompound.Plain(
        text = require<IRString>("text").value,
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
        interpret = optional<IRBoolean>("interpret")?.value ?: false,
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


private inline fun <reified T : IRElement> IRElement.requireTypeOf(field: String) =
    this as? T ?: error("Expected ${T::class} but found ${this::class} in $field")

private inline fun <reified T : IRElement> IRObject.require(key: String): T = this[key]?.requireTypeOf<T>(key) ?: error("$key not found")
private inline fun <reified T : IRElement> IRObject.optional(key: String): T? = this[key]?.requireTypeOf<T>(key)
