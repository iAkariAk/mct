package mct.text

import net.benwoodworth.knbt.NbtCompound

private val MAYBE_FIELDS = listOf(
    "text",
    "translate",
    "selector",
    "score",
    "nbt",
    "keybind",
)

private val MAYBE_FIELDS_AS_KEY = MAYBE_FIELDS.map { """"$it"\s*:\s*""".toRegex() }
internal fun String.isTextComponent() = MAYBE_FIELDS_AS_KEY.any { it.containsMatchIn(this)}
internal fun NbtCompound.isTextCompound() = MAYBE_FIELDS.any(this::containsKey)
