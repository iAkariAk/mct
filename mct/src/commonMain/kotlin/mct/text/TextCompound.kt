// Refer to https://zh.minecraft.wiki/w/%E6%96%87%E6%9C%AC%E7%BB%84%E4%BB%B6#%E7%BB%84%E4%BB%B6%E7%B1%BB%E5%9E%8B
package mct.text

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

fun TextCompound.isPlainStyle() =
    color == null && bold == null && italic == null && underlined == null && strikethrough == null && obfuscated == null


fun List<TextCompound>.multi() = when {
    isEmpty() -> error("Cannot be empty")
    size == 1 -> first()
    else -> first() + subList(1, size)
}

@Suppress("UNCHECKED_CAST")
inline operator fun <TC : TextCompound> TC.plus(extras: List<TextCompound>): TC =
    plus(extras) as TC

inline fun TextCompound.replaceText(text: String): TextCompound = when (this) {
    is TextCompound.Plain -> copy(text = text)
    else -> this
}

sealed interface TextCompoundOneOrMany {
    @JvmInline
    value class One(val value: TextCompound) : TextCompoundOneOrMany
    @JvmInline
    value class Many(val value: List<TextCompound>) : TextCompoundOneOrMany
}

inline fun TextCompound.one() = TextCompoundOneOrMany.One(this)
inline fun List<TextCompound>.many() = TextCompoundOneOrMany.Many(this)

@Serializable(TextCompoundSerializer::class)
sealed interface TextCompound {
    val extra: List<TextCompound>

    val color: String?
    val bold: Boolean?
    val italic: Boolean?
    val underlined: Boolean?
    val strikethrough: Boolean?
    val obfuscated: Boolean?

    operator fun plus(extras: List<TextCompound>): TextCompound

    fun substituteExtra(extra: List<TextCompound>): TextCompound

    @Serializable
    @SerialName("text")
    data class Plain(
        val text: String,
        override val extra: List<TextCompound> = emptyList(),

        override val color: String? = null,
        override val bold: Boolean? = null,
        override val italic: Boolean? = null,
        override val underlined: Boolean? = null,
        override val strikethrough: Boolean? = null,
        override val obfuscated: Boolean? = null,
    ) : TextCompound {
        override fun plus(extras: List<TextCompound>) = copy(extra = extra + extras)
        override fun substituteExtra(extra: List<TextCompound>) = copy(extra = extra)
    }

    @Serializable
    @SerialName("translatable")
    data class Translatable(
        val translate: String,
        val fallback: String? = null,
        val with: List<TextCompound> = emptyList(),
        override val extra: List<TextCompound> = emptyList(),

        override val color: String? = null,
        override val bold: Boolean? = null,
        override val italic: Boolean? = null,
        override val underlined: Boolean? = null,
        override val strikethrough: Boolean? = null,
        override val obfuscated: Boolean? = null,
    ) : TextCompound {
        override fun plus(extras: List<TextCompound>) = copy(extra = extra + extras)
        override fun substituteExtra(extra: List<TextCompound>) = copy(extra = extra)
    }

    @Serializable
    @SerialName("keybind")
    data class Keybind(
        val keybind: String,
        override val extra: List<TextCompound> = emptyList(),

        override val color: String? = null,
        override val bold: Boolean? = null,
        override val italic: Boolean? = null,
        override val underlined: Boolean? = null,
        override val strikethrough: Boolean? = null,
        override val obfuscated: Boolean? = null,
    ) : TextCompound {
        override fun plus(extras: List<TextCompound>) = copy(extra = extra + extras)
        override fun substituteExtra(extra: List<TextCompound>) = copy(extra = extra)
    }

    @Serializable
    @SerialName("score")
    data class Score(
        val score: Info,
        override val extra: List<TextCompound> = emptyList(),

        override val color: String? = null,
        override val bold: Boolean? = null,
        override val italic: Boolean? = null,
        override val underlined: Boolean? = null,
        override val strikethrough: Boolean? = null,
        override val obfuscated: Boolean? = null,
    ) : TextCompound {
        override fun plus(extras: List<TextCompound>) = copy(extra = extra + extras)
        override fun substituteExtra(extra: List<TextCompound>) = copy(extra = extra)

        @Serializable
        data class Info(
            val name: String,
            val objective: String,
        )
    }

    @Serializable
    @SerialName("selector")
    data class Selector(
        val selector: String,
        val separator: TextCompound? = null,
        override val extra: List<TextCompound> = emptyList(),

        override val color: String? = null,
        override val bold: Boolean? = null,
        override val italic: Boolean? = null,
        override val underlined: Boolean? = null,
        override val strikethrough: Boolean? = null,
        override val obfuscated: Boolean? = null,
    ) : TextCompound {
        override fun plus(extras: List<TextCompound>) = copy(extra = extra + extras)
        override fun substituteExtra(extra: List<TextCompound>) = copy(extra = extra)
    }

    @Serializable
    @SerialName("nbt")
    data class Nbt(
        val nbt: String,
        val interpret: Boolean = false,
        val separator: TextCompound? = null,

        val entity: String? = null,
        val block: String? = null,
        val storage: String? = null,

        override val extra: List<TextCompound> = emptyList(),

        override val color: String? = null,
        override val bold: Boolean? = null,
        override val italic: Boolean? = null,
        override val underlined: Boolean? = null,
        override val strikethrough: Boolean? = null,
        override val obfuscated: Boolean? = null,
    ) : TextCompound {
        override fun plus(extras: List<TextCompound>) = copy(extra = extra + extras)
        override fun substituteExtra(extra: List<TextCompound>) = copy(extra = extra)
    }

    @Serializable
    @SerialName("object")
    data class Object(
        val fallback: String? = null,
        val `object`: String,
        override val extra: List<TextCompound> = emptyList(),

        override val color: String? = null,
        override val bold: Boolean? = null,
        override val italic: Boolean? = null,
        override val underlined: Boolean? = null,
        override val strikethrough: Boolean? = null,
        override val obfuscated: Boolean? = null,
    ) : TextCompound {
        override fun plus(extras: List<TextCompound>) = copy(extra = extra + extras)
        override fun substituteExtra(extra: List<TextCompound>) = copy(extra = extra)
    }

    @Serializable
    @SerialName("sprite")
    data class Sprite(
        val sprite: String,
        override val extra: List<TextCompound> = emptyList(),

        override val color: String? = null,
        override val bold: Boolean? = null,
        override val italic: Boolean? = null,
        override val underlined: Boolean? = null,
        override val strikethrough: Boolean? = null,
        override val obfuscated: Boolean? = null,
    ) : TextCompound {
        override fun plus(extras: List<TextCompound>) = copy(extra = extra + extras)
        override fun substituteExtra(extra: List<TextCompound>) = copy(extra = extra)
    }
}


