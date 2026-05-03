package mct.text

import net.benwoodworth.knbt.NbtString

private val MAYBE_MAJOR_FIELDS = listOf(
    "text",
    "translate",
    "selector",
    "score",
    "nbt",
    "keybind",
)

private val ALL_FIELD = listOf(
    // Content
    "text", "translate", "with", "fallback",
    "score", "selector", "keybind",
    // NBT
    "nbt", "block", "entity", "storage",
    "interpret", "plain", "separator", "source",
    // Sprite / Object
    "object", "sprite", "atlas", "player",
    // Children & type
    "extra", "type",
    // Formatting
    "color", "font",
    "bold", "italic", "underlined", "strikethrough", "obfuscated",
    "shadow_color", "insertion",
    // Events
    "click_event", "hover_event",
)

private val MAYBE_FIELDS_AS_KEY = MAYBE_MAJOR_FIELDS.map { """"$it"\s*:\s*""".toRegex() }

internal fun String.isTextComponent() = MAYBE_FIELDS_AS_KEY.any { it.containsMatchIn(this) }

internal fun Map<String, *>.isTextCompound() =
    keys.all { ALL_FIELD.contains(it) } // to avoid misidentifying some compound including `text` or whatever, e.g. Display Entities includes `text`

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
