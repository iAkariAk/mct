package mct.cli.cmd.kits

import arrow.core.raise.Raise
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.colormath.model.SRGB
import com.github.ajalt.mordant.rendering.TextStyle
import com.github.ajalt.mordant.terminal.Terminal
import mct.MCTError
import mct.cli.BaseCommand
import mct.cli.panic
import mct.command.MCCommandJsonRight
import mct.model.text.TextComponent
import mct.model.text.TextComponentStyle
import mct.model.text.decodeToCompound
import mct.model.text.visitTextStyle
import mct.util.formatir.toIR
import mct.util.toJsonElementOrNull
import mct.util.toSnbtNbtTagOrNull
import mct.util.unreachable

class DisplayCommand : BaseCommand(name = "display", "Display TextComponent") {
    val format by option("--format", "-f").choice("json", "snbt", "auto").default("auto")
    val textComponentStr: String by argument("text-component")

    context(_: Raise<MCTError>)
    override suspend fun App() {
        val textComponent = when (format) {
            "json" -> textComponentStr.toJsonElementOrNull(MCCommandJsonRight)?.toIR()
            "snbt" -> textComponentStr.toSnbtNbtTagOrNull()?.toIR()
            "auto" -> (textComponentStr.toJsonElementOrNull()?.toIR() ?: textComponentStr.toSnbtNbtTagOrNull()?.toIR())
            else -> unreachable
        }?.decodeToCompound() ?: panic("No valid input")
        textComponent.render(terminal)
    }
}

private val FG_COLORS = mapOf(
    "black" to SRGB(0, 0, 0),
    "dark_blue" to SRGB(0, 0, 170),
    "dark_green" to SRGB(0, 170, 0),
    "dark_aqua" to SRGB(0, 170, 170),
    "dark_red" to SRGB(170, 0, 0),
    "dark_purple" to SRGB(170, 0, 170),
    "gold" to SRGB(255, 170, 0),
    "gray" to SRGB(170, 170, 170),
    "dark_gray" to SRGB(85, 85, 85),
    "blue" to SRGB(85, 85, 255),
    "green" to SRGB(85, 255, 85),
    "aqua" to SRGB(85, 255, 255),
    "red" to SRGB(255, 85, 85),
    "light_purple" to SRGB(255, 85, 255),
    "yellow" to SRGB(255, 255, 85),
    "white" to SRGB(255, 255, 255),
    "minecoin_gold" to SRGB(221, 214, 5),
    "material_quartz" to SRGB(227, 212, 209),
    "material_iron" to SRGB(206, 202, 202),
    "material_netherite" to SRGB(68, 58, 59),
    "material_redstone" to SRGB(151, 22, 7),
    "material_copper" to SRGB(180, 104, 77),
    "material_gold" to SRGB(222, 177, 45),
    "material_emerald" to SRGB(17, 160, 54),
    "material_diamond" to SRGB(44, 186, 168),
    "material_lapis" to SRGB(33, 73, 123),
    "material_amethyst" to SRGB(154, 92, 198),
    "material_resin" to SRGB(235, 114, 20),
    "party_blue_color" to SRGB(140, 179, 255),
)

private val BG_COLORS = mapOf(
    "black" to SRGB(0, 0, 0),
    "dark_blue" to SRGB(0, 0, 42),
    "dark_green" to SRGB(0, 42, 0),
    "dark_aqua" to SRGB(0, 42, 42),
    "dark_red" to SRGB(42, 0, 0),
    "dark_purple" to SRGB(42, 0, 42),
    "gold" to SRGB(64, 42, 0),
    "gray" to SRGB(42, 42, 42),
    "dark_gray" to SRGB(21, 21, 21),
    "blue" to SRGB(21, 21, 63),
    "green" to SRGB(21, 63, 21),
    "aqua" to SRGB(21, 63, 63),
    "red" to SRGB(63, 21, 21),
    "light_purple" to SRGB(63, 21, 63),
    "yellow" to SRGB(63, 63, 21),
    "white" to SRGB(63, 63, 63),
    "minecoin_gold" to SRGB(55, 53, 1),
    "material_quartz" to SRGB(56, 53, 52),
    "material_iron" to SRGB(51, 50, 50),
    "material_netherite" to SRGB(17, 14, 14),
    "material_redstone" to SRGB(37, 5, 1),
    "material_copper" to SRGB(45, 26, 19),
    "material_gold" to SRGB(55, 44, 11),
    "material_emerald" to SRGB(4, 40, 13),
    "material_diamond" to SRGB(11, 46, 42),
    "material_lapis" to SRGB(8, 18, 30),
    "material_amethyst" to SRGB(38, 23, 49),
    "material_resin" to SRGB(59, 29, 5),
    "party_blue_color" to SRGB(35, 45, 64),
)

private fun parseColor(code: String, isBg: Boolean = false) = when {
    code.startsWith('#') -> SRGB(code.substring(1))
    else -> if (isBg) BG_COLORS[code] else FG_COLORS[code]
}

private operator fun TextComponentStyle?.invoke(content: String): String {
    if (this == null) return content

    val style = TextStyle(
        color = color?.let { parseColor(it) ?: panic("No valid color: $color") },
        bold = bold,
        italic = italic,
        underline = underlined,
        strikethrough = strikethrough,
        inverse = obfuscated,
        dim = obfuscated,
    )
    return style(content)
}

private fun TextComponent<*>.render(terminal: Terminal) {
    val sb = StringBuilder()
    visitTextStyle { text, currentStyle ->
        val content = when (text) {
            is TextComponent.Keybind -> "[${text.keybind}]"
            is TextComponent.Nbt -> text.nbt
            is TextComponent.Object -> text.`object`
            is TextComponent.Plain -> text.text
            is TextComponent.Score -> "{${text.score.name}; ${text.score.objective}}"
            is TextComponent.Selector -> "<${text.selector}>"
            is TextComponent.Sprite -> text.sprite
            is TextComponent.Translatable -> TextStyle(hyperlink = text.fallback)(text.translate)
        }
        sb.append(currentStyle(content))
    }
    terminal.println(sb.toString())
}