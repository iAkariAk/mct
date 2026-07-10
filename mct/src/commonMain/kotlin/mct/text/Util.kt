package mct.text

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import mct.util.formatir.IRList
import mct.util.formatir.IRObject
import mct.util.formatir.IRString
import mct.util.snbt.SnbtCompound
import mct.util.snbt.SnbtList
import mct.util.snbt.SnbtString
import mct.util.surroundedBy
import mct.util.toJsonElementOrNull
import mct.util.toNbtTagOrNull
import mct.util.toRegex2
import net.benwoodworth.knbt.NbtCompound
import net.benwoodworth.knbt.NbtList
import net.benwoodworth.knbt.NbtString

private val MAYBE_MAJOR_FIELDS = hashSetOf(
    "text",
    "translate",
    "selector",
    "score",
    "nbt",
    "keybind",
)

private val ALL_FIELD = hashSetOf(
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

private val STRUCTURAL_FIELDS = hashSetOf(
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
    isTextCompoundShorthanded() || keys.all { ALL_FIELD.contains(it) } &&
            entries.all { (key, value) ->
                // Structural fields can have compound/list values
                if (key in STRUCTURAL_FIELDS) true
                // All other fields must be primitive leaf types (esp. text)
                else value !is Map<*, *> && value !is Collection<*>
            }

internal fun List<*>.isTextCompound(): Boolean = all {
    @Suppress("UNCHECKED_CAST")
    when {
        it is String || it is IRString || it is NbtString || it is SnbtString || (it is JsonPrimitive && it.isString) -> true
        it is List<*> || it is IRList || it is NbtList<*> || it is SnbtList || it is JsonArray -> it.isTextCompound()
        it is Map<*, *> || it is IRObject || it is NbtCompound || it is SnbtCompound || it is JsonObject -> it.keys.all { it is String } && (it as Map<String, *>).isTextCompound()
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
    "text" !in this && (this[""]?.let { it is String || it is IRString || it is NbtString || it is SnbtString || (it is JsonPrimitive && it.isString) }
        ?: false)

fun String.isTextCompoundSnbt() = trim().run {
    surroundedBy('"') || surroundedBy('\'') || surroundedBy('[', ']') || surroundedBy('{', '}')
} && toNbtTagOrNull()?.let {
    when (it) {
        is NbtCompound -> it.isTextCompound()
        is NbtList<*> -> it.isTextCompound()
        is NbtString -> true
        else -> false
    }
} ?: false

fun String.isTextCompoundJson() = trim().run {
    surroundedBy('"') || surroundedBy('[', ']') || surroundedBy('{', '}')
} && toJsonElementOrNull()?.let {
    when (it) {
        is JsonArray -> it.isTextCompound()
        is JsonObject -> it.isTextCompound()
        is JsonPrimitive if it.isString -> true
        else -> false
    }
} ?: false
