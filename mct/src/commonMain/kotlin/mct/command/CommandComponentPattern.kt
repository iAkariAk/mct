package mct.command

import kotlinx.serialization.Serializable
import mct.pointer.DataPointerPattern

typealias ComponentPatterns = List<ComponentPattern>

internal fun ComponentPatterns.findByCompoundKey(key: String) = find { pattern ->
    (pattern.namespace == "minecraft" && pattern.name == key) || "${pattern.namespace}:${pattern.name}" == key
}

@Serializable
data class ComponentPattern(
    val namespace: String = "minecraft",
    val name: String,
    val pattern: DataPointerPattern? = null
)

@Serializable
data class CustomizedComponentPattern(
    val namespace: String = "minecraft",
    val name: String,
    val pattern: DataPointerPattern? = null
) {
    fun compile() = ComponentPattern(namespace, name, pattern)
}


val BuiltinMinecraftComponentPatterns = buildList {
    fun P(name: String, pattern: DataPointerPattern? = null) =
        add(ComponentPattern(name = name, pattern = pattern))

    P("attribute_modifiers", DataPointerPattern.Equal(">#display>#value"))
    P("block_entity_data", DataPointerPattern.Right(">#CustomName"))
    P("custom_name")
    P("description")
    P("item_name")
    P("lore")
    P("text_display")
    listOf("sign_text_front", "sign_text_back").forEach {
        P(it, DataPointerPattern.Regex("^>#(?:filtered_)?messages$"))
    }
    P("written_book_content", DataPointerPattern.Regex("^>#(?:author|pages|title)(?:>#(?:filtered|raw))?$"))
    P("writable_book_content", DataPointerPattern.Regex("^>#pages(?:>#(?:filtered|raw))?$"))
}