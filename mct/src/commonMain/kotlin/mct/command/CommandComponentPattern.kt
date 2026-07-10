package mct.command

import kotlinx.serialization.Serializable
import mct.pointer.CustomizedDataPointerPattern
import mct.pointer.DataPointerPattern

typealias ComponentPatterns = List<ComponentPattern>

internal fun ComponentPatterns.findByCompoundKey(key: String) = find { pattern ->
    (pattern.namespace == "minecraft" && pattern.name == key) || "${pattern.namespace}:${pattern.name}" == key
}

data class ComponentPattern(
    val namespace: String = "minecraft",
    val name: String,
    val pattern: DataPointerPattern? = null
)

@Serializable
data class CustomizedComponentPattern(
    val namespace: String = "minecraft",
    val name: String,
    val pattern: CustomizedDataPointerPattern? = null
) {
    fun compile() = ComponentPattern(namespace, name, pattern?.compile())
}

private inline fun P(name: String, pattern: DataPointerPattern? = null) =
    ComponentPattern(name = name, pattern = pattern)

val BuiltinMinecraftComponentPatterns = listOf(
    P("custom_name"),
    P("item_name"),
    P("text_display"),
    P("description"),
    P("lore"),
    P("written_book_content", DataPointerPattern.Regex(">#(?:text|author|pages)$")),
    P("writable_book_content", DataPointerPattern.Right("pages")),
)