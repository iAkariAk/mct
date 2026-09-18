package mct.model.text

import mct.util.unreachable

data class TextComponentStyle(
    val color: String? = null,
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val underlined: Boolean? = null,
    val strikethrough: Boolean? = null,
    val obfuscated: Boolean? = null,
    val font: String? = null,
) {
    companion object {
        val Empty = TextComponentStyle()
    }
}


inline operator fun TextComponentStyle?.plus(child: TextComponentStyle?) = when {
    this == null && child == null -> null
    this == null && child != null -> child
    this != null && child == null -> this
    this != null && child != null -> TextComponentStyle(
        color = color ?: child.color,
        bold = bold ?: child.bold,
        italic = italic ?: child.italic,
        underlined = underlined ?: child.underlined,
        strikethrough = strikethrough ?: child.strikethrough,
        obfuscated = obfuscated ?: child.obfuscated,
        font = font ?: child.font,
    )

    else -> unreachable
}


fun TextComponent<*>.getStyleOrNull(): TextComponentStyle? = when (this) {
    is ManyTextComponent -> null
    is SingleTextComponent -> getStyle()
}

fun SingleTextComponent<*>.getStyle(): TextComponentStyle = TextComponentStyle(
    color = color,
    bold = bold,
    italic = italic,
    underlined = underlined,
    strikethrough = strikethrough,
    obfuscated = obfuscated,
    font = font
)

fun TextComponent<*>.visitTextStyle(
    parentStyle: TextComponentStyle? = null,
    operation: (text: SingleTextComponent<*>, currentStyle: TextComponentStyle?) -> Unit
) {
    when (this) {
        is ManyTextComponent -> components.forEach {
            it.visitTextStyle(parentStyle, operation)
        }

        is SingleTextComponent -> {
            val childStyle = getStyleOrNull()
            val currentStyle = parentStyle + childStyle
            operation(this, currentStyle)
            extra?.visitTextStyle(childStyle, operation)
        }
    }
}