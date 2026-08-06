// Refer to https://zh.minecraft.wiki/w/%E6%96%87%E6%9C%AC%E7%BB%84%E4%BB%B6#%E7%BB%84%E4%BB%B6%E7%B1%BB%E5%9E%8B
package mct.model.text

import kotlinx.serialization.Serializable
import mct.util.Regex2
import mct.util.formatir.*
import mct.util.unreachable

fun SingleTextCompound<*>.isPlainStyle() =
    color == null && bold == null && italic == null && underlined == null &&
            strikethrough == null && obfuscated == null


@Suppress("UNCHECKED_CAST")
inline fun <IR : IRElement, TC : TextCompound<IR>> TC.copy(): TC = TextCompound.fromIR(toIR()) as TC

@Suppress("UNCHECKED_CAST")
operator fun <IR : IRElement, TC : TextCompound<IR>> TC.plus(others: List<TextCompound<*>>): TC {
    val copy = copy()
    when (copy) {
        is ManyTextCompound -> copy.compounds = copy.compounds + others
        is SingleTextCompound<*> -> copy.extra = copy.extra?.plus(others) ?: ManyTextCompound(others)
    }
    return copy
}

fun SingleTextCompound<*>.replaceText(text: String): SingleTextCompound<*> {
    val copy = copy()
    if (copy is TextCompound.Plain) copy.text = text
    return copy
}

fun SingleTextCompound<*>.substitute(
    text: String? = null,
    extra: TextCompound<*>,
): SingleTextCompound<*> {
    val copy = copy()
    when (copy) {
        is TextCompound.Plain -> if (text != null) copy.text = text
        is TextCompound.Translatable -> if (text != null) copy.fallback = text
        else -> Unit
    }
    copy.extra = extra
    return copy
}

fun TextCompound<*>.hasText(): Boolean = when (this) {
    is SingleTextCompound<*> -> when (this) {
        is TextCompound.Selector -> separator?.hasText() == true || (extra?.hasText() == true)
        is TextCompound.Plain, is TextCompound.Translatable -> true
        else -> extra?.hasText() == true
    }

    is ManyTextCompound -> compounds.any { it.hasText() }
}

private val REGEX_TRANSLATE_KEY = Regex2("""[\w.]+\.+[\w.]+""")
fun String.isTranslateKey() = REGEX_TRANSLATE_KEY.matchEntire(this) != null
fun TextCompound<*>.isPureTranslateKeyCompound() = this is TextCompound.Translatable && with.isNullOrEmpty() && fallback == null && translate.isTranslateKey() && extra == null

fun TextCompound<*>.flatten() = when (this) {
    is ManyTextCompound -> compounds
    is SingleTextCompound<*> -> listOf(this)
}

inline fun IRElement.decodeToCompound(): TextCompound<*> = TextCompound.fromIR(this)

sealed class TextCompound<out IR : IRElement> {
    abstract val raw: IR
    protected val rawAsObj: IRObject? get() = raw as? IRObject

    abstract fun toIR(): IR

    class Plain(
        raw: IRElement,
    ) : SingleTextCompound<IRElement>(raw) {
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
            extra: TextCompound<*>? = null,
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
    ) : SingleTextCompound<IRObject>(raw) {
        var translate: String = raw.requiredString("translate")
        var fallback: String? = raw.optionalString("fallback")
        var with: List<TextCompound<*>>? = raw.optionalList("with") { fromIR(it) }

        constructor(
            translate: String,
            fallback: String? = null,
            with: List<TextCompound<*>>? = null,
            extra: TextCompound<*>? = null,
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
    ) : SingleTextCompound<IRObject>(raw) {
        var keybind: String = raw.requiredString("keybind")

        constructor(
            keybind: String,
            extra: TextCompound<*>? = null,
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
    ) : SingleTextCompound<IRObject>(raw) {
        private val scoreRaw = raw["score"] as? IRObject
            ?: throw TextCompoundCodecException("score must be an IRObject")
        var score: Info = Info(
            name = scoreRaw.requiredString("name"),
            objective = scoreRaw.requiredString("objective"),
        )

        constructor(
            score: Info,
            extra: TextCompound<*>? = null,
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
    ) : SingleTextCompound<IRObject>(raw) {
        var selector: String = raw.requiredString("selector")
        var separator: TextCompound<*>? = raw["separator"]?.let { TextCompound.fromIR(it) }

        constructor(
            selector: String,
            separator: TextCompound<*>? = null,
            extra: TextCompound<*>? = null,
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
    ) : SingleTextCompound<IRObject>(raw) {
        var nbt: String = raw.requiredString("nbt")
        var interpret: Boolean = raw.optionalBoolean("interpret") ?: false
        var separator: TextCompound<*>? = raw.optionalTextCompound("separator")
        var entity: String? = raw.optionalString("entity")
        var block: String? = raw.optionalString("block")
        var storage: String? = raw.optionalString("storage")

        constructor(
            nbt: String,
            interpret: Boolean = false,
            separator: TextCompound<*>? = null,
            entity: String? = null,
            block: String? = null,
            storage: String? = null,
            extra: TextCompound<*>? = null,
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
    ) : SingleTextCompound<IRObject>(raw) {
        var `object`: String = raw.requiredString("object")
        var fallback: String? = raw.optionalString("fallback")

        constructor(
            fallback: String? = null,
            `object`: String,
            extra: TextCompound<*>? = null,
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
    ) : SingleTextCompound<IRObject>(raw) {
        var sprite: String = raw.requiredString("sprite")

        constructor(
            sprite: String,
            extra: TextCompound<*>? = null,
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
        fun fromIR(ir: IRElement): TextCompound<*> = when (ir) {
                is IRString -> Plain(ir)
                is IRList -> ManyTextCompound(ir)
                is IRObject -> when (ir.componentType()) {
                    "text" -> Plain(ir)
                    "translate", "translatable" -> Translatable(ir)
                    "keybind" -> Keybind(ir)
                    "score" -> Score(ir)
                    "selector" -> Selector(ir)
                    "nbt" -> Nbt(ir)
                    "object" -> Object(ir)
                    "sprite" -> Sprite(ir)
                    else -> throw TextCompoundCodecException("Unknown TextCompound type: $ir")
                }

                else -> throw TextCompoundCodecException(
                    "TextCompound raw must be IRString, IRObject, or IRList, but was ${ir::class.simpleName}",
                )
            }
    }
}

class ManyTextCompound(
    override val raw: IRList,
) : TextCompound<IRList>() {

    constructor(compounds: List<TextCompound<*>>) : this(IRList(compounds.map { it.toIR() }))
    constructor(vararg compounds: TextCompound<*>) : this(compounds.asList())

    var compounds: List<TextCompound<*>> = raw.map { TextCompound.fromIR(it) }

    override fun toIR(): IRList = IRList(compounds.map { it.toIR() })
}

sealed class SingleTextCompound<out IR : IRElement>(
    final override val raw: IR,
) : TextCompound<IR>() {
    var extra: TextCompound<*>? = rawAsObj?.optionalTextCompound("extra")
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
            ?: throw TextCompoundCodecException("type must be an IRString")
    }
    return listOf("text", "translate", "keybind", "score", "selector", "nbt", "object", "sprite")
        .firstOrNull(::containsKey)
}

private fun IRObject.requiredString(key: String): String =
    optionalString(key) ?: throw TextCompoundCodecException("$key must exist")

private fun IRObject.optionalString(key: String): String? = when (val value = this[key]) {
    null -> null
    is IRString -> value.value
    else -> throw TextCompoundCodecException("$key must be an IRString")
}

private fun IRObject.optionalBoolean(key: String): Boolean? = when (val value = this[key]) {
    null -> null
    is IRBoolean -> value.value
    is IRByte -> value.value != 0.toByte()
    else -> throw TextCompoundCodecException("$key must be an IRBoolean or IRByte")
}

private fun <T> IRObject.optionalList(key: String, convert: (IRElement) -> T): List<T>? = when (val value = this[key]) {
    null -> null
    is IRList -> value.map(convert)
    else -> throw TextCompoundCodecException("$key must be a list")
}

private fun IRObject.optionalTextCompound(key: String): TextCompound<*>? = when (val value = this[key]) {
    null -> null
    is IRString -> TextCompound.Plain(value)
    is IRObject, is IRList -> TextCompound.fromIR(value)
    else -> throw TextCompoundCodecException("$key must be a text compound")
}

private fun plainRaw(
    text: String,
    extra: TextCompound<*>?,
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
    extra: TextCompound<*>?,
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
