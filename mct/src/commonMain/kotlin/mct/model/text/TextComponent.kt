// Refer to https://zh.minecraft.wiki/w/%E6%96%87%E6%9C%AC%E7%BB%84%E4%BB%B6#%E7%BB%84%E4%BB%B6%E7%B1%BB%E5%9E%8B
package mct.model.text

import kotlinx.serialization.Serializable
import mct.util.Regex2
import mct.util.formatir.*
import mct.util.unreachable

fun SingleTextComponent<*>.isPlainStyle() =
    color == null && bold == null && italic == null && underlined == null &&
            strikethrough == null && obfuscated == null


@Suppress("UNCHECKED_CAST")
inline fun <IR : IRElement, TC : TextComponent<IR>> TC.copy(): TC = TextComponent.fromIR(toIR()) as TC

@Suppress("UNCHECKED_CAST")
operator fun <IR : IRElement, TC : TextComponent<IR>> TC.plus(others: List<TextComponent<*>>): TC {
    val copy = copy()
    when (copy) {
        is ManyTextComponent -> copy.compounds = copy.compounds + others
        is SingleTextComponent<*> -> copy.extra = copy.extra?.plus(others) ?: ManyTextComponent(others)
    }
    return copy
}

fun SingleTextComponent<*>.replaceText(text: String): SingleTextComponent<*> {
    val copy = copy()
    if (copy is TextComponent.Plain) copy.text = text
    return copy
}

fun SingleTextComponent<*>.substitute(
    text: String? = null,
    extra: TextComponent<*>,
): SingleTextComponent<*> {
    val copy = copy()
    when (copy) {
        is TextComponent.Plain -> if (text != null) copy.text = text
        is TextComponent.Translatable -> if (text != null) copy.fallback = text
        else -> Unit
    }
    copy.extra = extra
    return copy
}

fun TextComponent<*>.hasText(): Boolean = when (this) {
    is SingleTextComponent<*> -> when (this) {
        is TextComponent.Selector -> separator?.hasText() == true || (extra?.hasText() == true)
        is TextComponent.Plain, is TextComponent.Translatable -> true
        else -> extra?.hasText() == true
    }

    is ManyTextComponent -> compounds.any { it.hasText() }
}

private val REGEX_TRANSLATE_KEY = Regex2("""[\w.]+\.+[\w.]+""")
fun String.isTranslateKey() = REGEX_TRANSLATE_KEY.matchEntire(this) != null
fun TextComponent<*>.isPureTranslateKeyCompound() = this is TextComponent.Translatable && with.isNullOrEmpty() && fallback == null && translate.isTranslateKey() && extra == null

fun TextComponent<*>.flatten() = when (this) {
    is ManyTextComponent -> compounds
    is SingleTextComponent<*> -> listOf(this)
}

inline fun IRElement.decodeToCompound(): TextComponent<*> = TextComponent.fromIR(this)

sealed class TextComponent<out IR : IRElement> {
    abstract val raw: IR
    protected val rawAsObj: IRObject? get() = raw as? IRObject

    abstract fun toIR(): IR

    class Plain(
        raw: IRElement,
    ) : SingleTextComponent<IRElement>(raw) {
        init {
            require(raw is IRString || raw is IRObject) {
                "Plain raw must be IRString or IRObject, but was ${raw::class.simpleName}"
            }
        }

        var text: String = when (raw) {
            is IRString -> raw.value
            is IRObject -> raw.requiredString("text")
            else -> unreachable
        }

        constructor(
            text: String,
            extra: TextComponent<*>? = null,
            color: String? = null,
            bold: Boolean? = null,
            italic: Boolean? = null,
            underlined: Boolean? = null,
            strikethrough: Boolean? = null,
            obfuscated: Boolean? = null,
            font: String? = null
        ) : this(plainRaw(text, extra, color, bold, italic, underlined, strikethrough, obfuscated, font))

        override fun toIR(): IRElement = when (val source = raw) {
            is IRString -> plainRaw(text, extra, color, bold, italic, underlined, strikethrough, obfuscated, font)
            is IRObject -> IRObject(copyRawWithCommonFields(source).apply {
                this["text"] = IRString(text)
            })

            else -> unreachable
        }
    }

    class Translatable(
        raw: IRObject,
    ) : SingleTextComponent<IRObject>(raw) {
        var translate: String = raw.requiredString("translate")
        var fallback: String? = raw.optionalString("fallback")
        var with: List<TextComponent<*>>? = raw.optionalList("with") { fromIR(it) }

        constructor(
            translate: String,
            fallback: String? = null,
            with: List<TextComponent<*>>? = null,
            extra: TextComponent<*>? = null,
            color: String? = null,
            bold: Boolean? = null,
            italic: Boolean? = null,
            underlined: Boolean? = null,
            strikethrough: Boolean? = null,
            obfuscated: Boolean? = null,
            font: String? = null
        ) : this(buildComponentRaw(extra, color, bold, italic, underlined, strikethrough, obfuscated, font) {
            put("translate", translate)
            putIfPresent("fallback", fallback)
            putIfPresent("with", with?.map { it.toIR() }?.let(::IRList))
        })

        override fun toIR(): IRObject = IRObject(copyRawWithCommonFields(raw).apply {
            this["translate"] = IRString(translate)
            setOptional("fallback", fallback?.let(::IRString))
            setOptional("with", with?.map { it.toIR() }?.let(::IRList))
        })
    }

    class Keybind(
        raw: IRObject,
    ) : SingleTextComponent<IRObject>(raw) {
        var keybind: String = raw.requiredString("keybind")

        constructor(
            keybind: String,
            extra: TextComponent<*>? = null,
            color: String? = null,
            bold: Boolean? = null,
            italic: Boolean? = null,
            underlined: Boolean? = null,
            strikethrough: Boolean? = null,
            obfuscated: Boolean? = null,
            font: String? = null
        ) : this(buildComponentRaw(extra, color, bold, italic, underlined, strikethrough, obfuscated, font) {
            put("keybind", keybind)
        })

        override fun toIR(): IRObject = IRObject(copyRawWithCommonFields(raw).apply {
            this["keybind"] = IRString(keybind)
        })
    }

    class Score(
        raw: IRObject,
    ) : SingleTextComponent<IRObject>(raw) {
        private val scoreRaw = raw["score"] as? IRObject
            ?: throw TextComponentCodecException("score must be an IRObject")
        var score: Info = Info(
            name = scoreRaw.requiredString("name"),
            objective = scoreRaw.requiredString("objective"),
        )

        constructor(
            score: Info,
            extra: TextComponent<*>? = null,
            color: String? = null,
            bold: Boolean? = null,
            italic: Boolean? = null,
            underlined: Boolean? = null,
            strikethrough: Boolean? = null,
            obfuscated: Boolean? = null,
            font: String? = null
        ) : this(buildComponentRaw(extra, color, bold, italic, underlined, strikethrough, obfuscated, font) {
            put("score", score.toIR())
        })

        override fun toIR(): IRObject {
            val mutable = copyRawWithCommonFields(raw)
            val scoreFields = scoreRaw.value.toMutableMap()
            scoreFields["name"] = IRString(score.name)
            scoreFields["objective"] = IRString(score.objective)
            mutable["score"] = IRObject(scoreFields)
            return IRObject(mutable)
        }

        @Serializable
        data class Info(
            val name: String,
            val objective: String,
        ) {
            internal fun toIR() = buildIRObject {
                put("name", name)
                put("objective", objective)
            }
        }
    }

    class Selector(
        raw: IRObject,
    ) : SingleTextComponent<IRObject>(raw) {
        var selector: String = raw.requiredString("selector")
        var separator: TextComponent<*>? = raw["separator"]?.let { TextComponent.fromIR(it) }

        constructor(
            selector: String,
            separator: TextComponent<*>? = null,
            extra: TextComponent<*>? = null,
            color: String? = null,
            bold: Boolean? = null,
            italic: Boolean? = null,
            underlined: Boolean? = null,
            strikethrough: Boolean? = null,
            obfuscated: Boolean? = null,
            font: String? = null
        ) : this(buildComponentRaw(extra, color, bold, italic, underlined, strikethrough, obfuscated, font) {
            put("selector", selector)
            putIfPresent("separator", separator?.toIR())
        })

        override fun toIR(): IRObject = IRObject(copyRawWithCommonFields(raw).apply {
            this["selector"] = IRString(selector)
            setOptional("separator", separator?.toIR())
        })
    }

    class Nbt(
        raw: IRObject,
    ) : SingleTextComponent<IRObject>(raw) {
        var nbt: String = raw.requiredString("nbt")
        var interpret: Boolean = raw.optionalBoolean("interpret") ?: false
        var separator: TextComponent<*>? = raw.optionalTextComponent("separator")
        var entity: String? = raw.optionalString("entity")
        var block: String? = raw.optionalString("block")
        var storage: String? = raw.optionalString("storage")

        constructor(
            nbt: String,
            interpret: Boolean = false,
            separator: TextComponent<*>? = null,
            entity: String? = null,
            block: String? = null,
            storage: String? = null,
            extra: TextComponent<*>? = null,
            color: String? = null,
            bold: Boolean? = null,
            italic: Boolean? = null,
            underlined: Boolean? = null,
            strikethrough: Boolean? = null,
            obfuscated: Boolean? = null,
            font: String? = null
        ) : this(buildComponentRaw(extra, color, bold, italic, underlined, strikethrough, obfuscated, font) {
            put("nbt", nbt)
            if (interpret) put("interpret", true)
            putIfPresent("separator", separator?.toIR())
            putIfPresent("entity", entity)
            putIfPresent("block", block)
            putIfPresent("storage", storage)
        })

        override fun toIR(): IRObject = IRObject(copyRawWithCommonFields(raw).apply {
            this["nbt"] = IRString(nbt)
            this["interpret"] = IRBoolean(interpret)
            setOptional("separator", separator?.toIR())
            setOptional("entity", entity?.let(::IRString))
            setOptional("block", block?.let(::IRString))
            setOptional("storage", storage?.let(::IRString))
        })
    }

    class Object(
        raw: IRObject,
    ) : SingleTextComponent<IRObject>(raw) {
        var `object`: String = raw.requiredString("object")
        var fallback: String? = raw.optionalString("fallback")

        constructor(
            fallback: String? = null,
            `object`: String,
            extra: TextComponent<*>? = null,
            color: String? = null,
            bold: Boolean? = null,
            italic: Boolean? = null,
            underlined: Boolean? = null,
            strikethrough: Boolean? = null,
            obfuscated: Boolean? = null,
            font: String? = null
        ) : this(buildComponentRaw(extra, color, bold, italic, underlined, strikethrough, obfuscated, font) {
            put("object", `object`)
            putIfPresent("fallback", fallback)
        })

        override fun toIR(): IRObject = IRObject(copyRawWithCommonFields(raw).apply {
            this["object"] = IRString(`object`)
            setOptional("fallback", fallback?.let(::IRString))
        })
    }

    class Sprite(
        raw: IRObject,
    ) : SingleTextComponent<IRObject>(raw) {
        var sprite: String = raw.requiredString("sprite")

        constructor(
            sprite: String,
            extra: TextComponent<*>? = null,
            color: String? = null,
            bold: Boolean? = null,
            italic: Boolean? = null,
            underlined: Boolean? = null,
            strikethrough: Boolean? = null,
            obfuscated: Boolean? = null,
            font: String? = null
        ) : this(buildComponentRaw(extra, color, bold, italic, underlined, strikethrough, obfuscated, font) {
            put("sprite", sprite)
        })

        override fun toIR(): IRObject = IRObject(copyRawWithCommonFields(raw).apply {
            this["sprite"] = IRString(sprite)
        })
    }

    companion object {
        fun fromIR(ir: IRElement): TextComponent<*> = when (ir) {
                is IRString -> Plain(ir)
                is IRList -> ManyTextComponent(ir)
                is IRObject -> when (ir.componentType()) {
                    "text" -> Plain(ir)
                    "translate", "translatable" -> Translatable(ir)
                    "keybind" -> Keybind(ir)
                    "score" -> Score(ir)
                    "selector" -> Selector(ir)
                    "nbt" -> Nbt(ir)
                    "object" -> Object(ir)
                    "sprite" -> Sprite(ir)
                    else -> throw TextComponentCodecException("Unknown TextComponent type: $ir")
                }

                else -> throw TextComponentCodecException(
                    "TextComponent raw must be IRString, IRObject, or IRList, but was ${ir::class.simpleName}",
                )
            }
    }
}

class ManyTextComponent(
    override val raw: IRList,
) : TextComponent<IRList>() {

    constructor(compounds: List<TextComponent<*>>) : this(IRList(compounds.map { it.toIR() }))
    constructor(vararg compounds: TextComponent<*>) : this(compounds.asList())

    var compounds: List<TextComponent<*>> = raw.map { TextComponent.fromIR(it) }

    override fun toIR(): IRList = IRList(compounds.map { it.toIR() })
}

sealed class SingleTextComponent<out IR : IRElement>(
    final override val raw: IR,
) : TextComponent<IR>() {
    var extra: TextComponent<*>? = rawAsObj?.optionalTextComponent("extra")
    var color: String? = rawAsObj?.optionalString("color")
    var bold: Boolean? = rawAsObj?.optionalBoolean("bold")
    var italic: Boolean? = rawAsObj?.optionalBoolean("italic")
    var underlined: Boolean? = rawAsObj?.optionalBoolean("underlined")
    var strikethrough: Boolean? = rawAsObj?.optionalBoolean("strikethrough")
    var obfuscated: Boolean? = rawAsObj?.optionalBoolean("obfuscated")
    var font: String? = rawAsObj?.optionalString("font")

    protected fun copyRawWithCommonFields(
        raw: IRObject,
    ): MutableMap<String, IRElement> = raw.value.toMutableMap().apply {
        setOptional("extra", extra?.toIR())
        setOptional("color", color?.let(::IRString))
        setOptional("bold", bold?.let(::IRBoolean))
        setOptional("italic", italic?.let(::IRBoolean))
        setOptional("underlined", underlined?.let(::IRBoolean))
        setOptional("strikethrough", strikethrough?.let(::IRBoolean))
        setOptional("obfuscated", obfuscated?.let(::IRBoolean))
        setOptional("font", font?.let(::IRString))
    }
}

private fun IRObject.componentType(): String? {
    val explicit = this["type"]
    if (explicit != null) {
        return (explicit as? IRString)?.value
            ?: throw TextComponentCodecException("type must be an IRString")
    }
    return listOf("text", "translate", "keybind", "score", "selector", "nbt", "object", "sprite")
        .firstOrNull(::containsKey)
}

private fun IRObject.requiredString(key: String): String =
    optionalString(key) ?: throw TextComponentCodecException("$key must exist")

private fun IRObject.optionalString(key: String): String? = when (val value = this[key]) {
    null -> null
    is IRString -> value.value
    else -> throw TextComponentCodecException("$key must be an IRString")
}

private fun IRObject.optionalBoolean(key: String): Boolean? = when (val value = this[key]) {
    null -> null
    is IRBoolean -> value.value
    is IRByte -> value.value != 0.toByte()
    else -> throw TextComponentCodecException("$key must be an IRBoolean or IRByte")
}

private fun <T> IRObject.optionalList(key: String, convert: (IRElement) -> T): List<T>? = when (val value = this[key]) {
    null -> null
    is IRList -> value.map(convert)
    else -> throw TextComponentCodecException("$key must be a list")
}

private fun IRObject.optionalTextComponent(key: String): TextComponent<*>? = when (val value = this[key]) {
    null -> null
    is IRString -> TextComponent.Plain(value)
    is IRObject, is IRList -> TextComponent.fromIR(value)
    else -> throw TextComponentCodecException("$key must be a text compound")
}

private fun plainRaw(
    text: String,
    extra: TextComponent<*>?,
    color: String?,
    bold: Boolean?,
    italic: Boolean?,
    underlined: Boolean?,
    strikethrough: Boolean?,
    obfuscated: Boolean?,
    font: String?
): IRElement {
    if (extra == null && color == null && bold == null && italic == null && underlined == null &&
        strikethrough == null && obfuscated == null && font == null
    ) return IRString(text)

    return buildComponentRaw(extra, color, bold, italic, underlined, strikethrough, obfuscated, font) {
        put("text", text)
    }
}

private fun buildComponentRaw(
    extra: TextComponent<*>?,
    color: String?,
    bold: Boolean?,
    italic: Boolean?,
    underlined: Boolean?,
    strikethrough: Boolean?,
    obfuscated: Boolean?,
    font: String?,
    content: IRObjectBuilder.() -> Unit,
) = buildIRObject {
    content()
    putIfPresent("extra", extra?.toIR())
    putIfPresent("color", color)
    putIfPresent("bold", bold)
    putIfPresent("italic", italic)
    putIfPresent("underlined", underlined)
    putIfPresent("strikethrough", strikethrough)
    putIfPresent("obfuscated", obfuscated)
    putIfPresent("font", font)
}

private fun MutableMap<String, IRElement>.setOptional(
    key: String,
    value: IRElement?,
) {
    if (value == null) remove(key) else this[key] = value
}
