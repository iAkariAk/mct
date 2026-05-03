package mct.text

import net.benwoodworth.knbt.NbtString

private val MAYBE_FIELDS = listOf(
    "text",
    "translate",
    "selector",
    "score",
    "nbt",
    "keybind",
)

private val MAYBE_FIELDS_AS_KEY = MAYBE_FIELDS.map { """"$it"\s*:\s*""".toRegex() }
internal fun String.isTextComponent() = MAYBE_FIELDS_AS_KEY.any { it.containsMatchIn(this) }

internal fun Map<String, *>.isTextCompound() = MAYBE_FIELDS.any(this::containsKey)
// "minecraft:lore": [
//  {
//    "": " " // Here are a shorthand for "text"
//  },
//  {
//    italic: 0b,
//    underlined: 1b,
//    text: "Legendary Item",
//    color: "gold"
//  }
//],
internal fun Map<String, *>.isTextCompoundShorthanded() =
    "text" !in this && (this[""]?.let { it is NbtString } ?: false)
