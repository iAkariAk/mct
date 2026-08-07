package mct.model.text

import mct.util.formatir.IRElement
import mct.util.formatir.IRList
import mct.util.formatir.IRObject
import mct.util.formatir.IRString

fun TextComponent<*>.encodeToIR(simplify: Boolean = false): IRElement =
    if (simplify) simplifiedIR() else toIR()

private fun TextComponent<*>.simplifiedIR(): IRElement = when (this) {
    is ManyTextComponent -> IRList(compounds.map { it.simplifiedIR() })
    is TextComponent.Plain -> {
        if (!isPlainStyle() || raw.hasUnmanagedPlainFields()) return toIR()
        val self = IRString(text)
        val _extra = extra
        if (_extra == null) self else IRList(listOf(self) + _extra.flatten().map { it.simplifiedIR() })
    }

    else -> toIR()
}

private val managedPlainFields = setOf(
    "type",
    "text",
    "extra",
    "color",
    "bold",
    "italic",
    "underlined",
    "strikethrough",
    "obfuscated",
)

private fun IRElement.hasUnmanagedPlainFields(): Boolean =
    this is IRObject && keys.any { it !in managedPlainFields }

