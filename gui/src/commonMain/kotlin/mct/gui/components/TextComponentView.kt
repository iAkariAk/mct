package mct.gui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import mct.command.MCCommandJsonRight
import mct.gui.model.ComponentFormat
import mct.model.text.TextComponent
import mct.model.text.TextComponentStyle
import mct.model.text.decodeToCompound
import mct.model.text.visitTextStyle
import mct.util.formatir.toIR
import mct.util.toJsonElementOrNull
import mct.util.toSnbtNbtTagOrNull

/**
 * Parse a text component the way `mct kit display text` does: the outer form is JSON or SNBT
 * (or both, tried in that order), and the result is decoded into the [TextComponent] tree the game
 * would render.
 *
 * Returns `null` for anything malformed instead of throwing: the input comes from a text field that
 * is invalid for most of the time the user is typing in it.
 */
fun parseTextComponent(raw: String, format: ComponentFormat): TextComponent<*>? {
    if (raw.isBlank()) return null
    return runCatching {
        when (format) {
            ComponentFormat.Json -> raw.toJsonElementOrNull(MCCommandJsonRight)?.toIR()
            ComponentFormat.Snbt -> raw.toSnbtNbtTagOrNull()?.toIR()
            ComponentFormat.Auto -> raw.toJsonElementOrNull()?.toIR() ?: raw.toSnbtNbtTagOrNull()?.toIR()
        }?.decodeToCompound()
    }.getOrNull()
}

/**
 * The text component as Minecraft would show it: colours, bold / italic / underline and strike
 * through are applied per node, exactly as the CLI's terminal renderer applies them.
 *
 * Obfuscated runs are shown as their underlying text: the game scrambles them at render time, and
 * nothing here knows which characters the font would pick.
 */
@Composable
fun TextComponentPreview(
    text: TextComponent<*>,
    modifier: Modifier = Modifier,
) {
    val annotated = remember(text) { text.toAnnotatedString() }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        SelectionContainer {
            Text(
                annotated,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

private fun TextComponent<*>.toAnnotatedString(): AnnotatedString = buildAnnotatedString {
    visitTextStyle { node, style ->
        val content = when (node) {
            is TextComponent.Keybind -> "[${node.keybind}]"
            is TextComponent.Nbt -> node.nbt
            is TextComponent.Object -> node.`object`
            is TextComponent.Plain -> node.text
            is TextComponent.Score -> "{${node.score.name}; ${node.score.objective}}"
            is TextComponent.Selector -> "<${node.selector}>"
            is TextComponent.Sprite -> node.sprite
            is TextComponent.Translatable -> node.fallback ?: node.translate
        }
        val span = style?.toSpanStyle()
        if (span == null) append(content) else withStyle(span) { append(content) }
    }
}

/** The named colours Minecraft accepts; a `#rrggbb` string resolves to the hex value itself. */
private val MinecraftTextColors: Map<String, Color> = mapOf(
    "black" to Color(0xFF000000),
    "dark_blue" to Color(0xFF0000AA),
    "dark_green" to Color(0xFF00AA00),
    "dark_aqua" to Color(0xFF00AAAA),
    "dark_red" to Color(0xFFAA0000),
    "dark_purple" to Color(0xFFAA00AA),
    "gold" to Color(0xFFFFAA00),
    "gray" to Color(0xFFAAAAAA),
    "dark_gray" to Color(0xFF555555),
    "blue" to Color(0xFF5555FF),
    "green" to Color(0xFF55FF55),
    "aqua" to Color(0xFF55FFFF),
    "red" to Color(0xFFFF5555),
    "light_purple" to Color(0xFFFF55FF),
    "yellow" to Color(0xFFFFFF55),
    "white" to Color(0xFFFFFFFF),
    "minecoin_gold" to Color(0xFFDDD605),
    "material_quartz" to Color(0xFFE3D4D1),
    "material_iron" to Color(0xFFCECACA),
    "material_netherite" to Color(0xFF443A3B),
    "material_redstone" to Color(0xFF971607),
    "material_copper" to Color(0xFFB4684D),
    "material_gold" to Color(0xFFDEB12D),
    "material_emerald" to Color(0xFF11A036),
    "material_diamond" to Color(0xFF2CBAA8),
    "material_lapis" to Color(0xFF33497B),
    "material_amethyst" to Color(0xFF9A5CC6),
    "material_resin" to Color(0xFFEB7214),
    "party_blue_color" to Color(0xFF8CB3FF),
)

/** `null` when the node carries no visual style at all, so the text keeps the theme's own colour. */
private fun TextComponentStyle.toSpanStyle(): SpanStyle? {
    val color = color?.let { name ->
        if (name.startsWith('#')) name.drop(1).toIntOrNull(16)?.let { Color(0xFF000000.toInt() or it) }
        else MinecraftTextColors[name]
    }
    if (color == null && bold != true && italic != true && underlined != true && strikethrough != true) return null
    return SpanStyle(
        color = color ?: Color.Unspecified,
        fontWeight = if (bold == true) FontWeight.Bold else null,
        fontStyle = if (italic == true) FontStyle.Italic else null,
        textDecoration = when {
            underlined == true && strikethrough == true ->
                TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))

            underlined == true -> TextDecoration.Underline
            strikethrough == true -> TextDecoration.LineThrough
            else -> null
        },
    )
}
