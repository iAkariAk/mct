package mct.model.text

import mct.util.formatir.IRElement
import mct.util.formatir.IRList
import mct.util.formatir.IRObject
import mct.util.formatir.IRString

fun TextCompound<*>.encodeToIR(simplify: Boolean = false): IRElement =
    if (simplify) simplifiedIR() else toIR()

private fun TextCompound<*>.simplifiedIR(): IRElement = when (this) {
    is ManyTextCompound -> IRList(compounds.map { it.simplifiedIR() })
    is TextCompound.Plain -> {
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

