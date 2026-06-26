package mct.text

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import mct.util.snbt.SnbtCompound
import mct.util.snbt.SnbtList
import mct.util.snbt.SnbtString
import mct.util.toRegex2
import net.benwoodworth.knbt.NbtCompound
import net.benwoodworth.knbt.NbtList
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
    "object", "sprite", "atlas", "player", "hat",
    // Children & type
    "extra", "type",
    // Formatting
    "color", "font",
    "bold", "italic", "underlined", "strikethrough", "obfuscated",
    "shadow_color", "insertion",
    // Events
    "click_event", "hover_event",
)

private val MAYBE_FIELDS_AS_KEY = MAYBE_MAJOR_FIELDS.map { """"$it"\s*:\s*""".toRegex2() }

internal fun String.isTextComponent() = MAYBE_FIELDS_AS_KEY.any { it.containsMatchIn(this) }

private val STRUCTURAL_FIELDS = listOf(
    // Fields that legitimately hold compound/list values in text components
    "extra", "with",
    "hover_event", "click_event",
    "score", "separator",
    // player can be a compound (profile data) in object type "player" (1.21.5+)
    "player",
    // shadow_color can be a list of 4 floats [R,G,B,Opacity] (1.21.5+)
    "shadow_color",
)

internal fun Map<String, *>.isTextCompound() =
    keys.all { ALL_FIELD.contains(it) } &&
            entries.all { (key, value) ->
                // Structural fields can have compound/list values
                if (key in STRUCTURAL_FIELDS) true
                // All other fields must be primitive leaf types (esp. text)
                else value !is Map<*, *> && value !is Collection<*>
            }

internal fun List<*>.isTextCompound(): Boolean = all {
    @Suppress("UNCHECKED_CAST")
    when {
        it is String || it is NbtString || it is SnbtString || (it is JsonPrimitive && it.isString) -> true
        it is List<*> || it is NbtList<*> || it is SnbtList || it is JsonArray -> it.isTextCompound()
        it is Map<*, *> || it is NbtCompound || it is SnbtCompound || it is JsonObject -> it.keys.all { it is String } && (it as Map<String, *>).isTextCompound()
        else -> false
    }
}


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
    "text" !in this && (this[""]?.let { it is NbtString || it is SnbtString } ?: false)
