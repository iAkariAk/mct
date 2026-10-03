package mct.cli.cmd.kits

import arrow.core.raise.Raise
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextColors.yellow
import com.github.ajalt.mordant.rendering.TextStyles.bold
import com.github.ajalt.mordant.terminal.Terminal
import mct.MCTError
import mct.cli.BaseCommand
import mct.cli.panic
import mct.cli.path
import mct.cli.render.renderOn
import mct.command.MCCommandJsonRight
import mct.kit.TranslationMapping
import mct.kit.TranslationPool
import mct.model.text.decodeToCompound
import mct.util.formatir.toIR
import mct.util.io.readJson
import mct.util.toJsonElementOrNull
import mct.util.toSnbtNbtTagOrNull
import mct.util.unreachable

class DisplayCommand : BaseCommand(name = "display", "Display content with highlight") {
    init {
        subcommands(DisplayTextCommand(), DisplayFileCommand())
    }
}

private class DisplayTextCommand : BaseCommand(name = "text", "Display TextComponent") {
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
        textComponent.renderOn(terminal)
    }
}


private class DisplayFileCommand : BaseCommand(name = "file", "Display some files with highlight") {
    init {
        subcommands(Mappings(), Missing())
    }

    private class Mappings : FileDisplay("mappings") {
        context(_: Raise<MCTError>)
        override suspend fun App() {
            val mappings = input.readJson<TranslationMapping>()
            mappings.forEach { (key, value) ->
                terminal.renderHighlight(key, false)
                terminal.print((yellow + bold)(" ==> "))
                value?.let(terminal::renderHighlight) ?: terminal.print(TextColors.green("keep the original"))
            }
        }
    }

    private class Missing : FileDisplay("missing") {
        context(_: Raise<MCTError>)
        override suspend fun App() {
            val mappings = input.readJson<TranslationPool>()
            mappings.forEach { item ->
                terminal.renderHighlight(item, true)
            }
        }
    }

    private sealed class FileDisplay(name: String) : BaseCommand(name, "Display $name with highlight") {
        val input by option("--input", "-i").path().required()
    }
}

private fun Terminal.renderHighlight(raw: String, newline: Boolean = true) {
    runCatching { parseComponentOrNull(raw)?.renderOn(this@renderHighlight, newline) }.getOrNull() ?: render(raw)
}

private fun parseComponentOrNull(raw: String) =
    (raw.toJsonElementOrNull()?.toIR() ?: raw.toSnbtNbtTagOrNull()?.toIR())?.decodeToCompound()
