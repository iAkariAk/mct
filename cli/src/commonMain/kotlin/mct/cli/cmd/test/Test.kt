package mct.cli.cmd.test

import arrow.core.raise.Raise
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.mordant.rendering.TextColors.blue
import com.github.ajalt.mordant.rendering.TextColors.green
import com.github.ajalt.mordant.rendering.TextStyles.bold
import mct.MCTError
import mct.cli.*
import mct.command.extractTextFromCommands
import mct.dp.mcjson.BuiltinMCJsonPatterns
import mct.nbt.BuiltinNbtPatterns
import mct.pointer.DataPointer
import mct.pointer.DataPointerPattern
import mct.pointer.decodeFromString
import mct.pointer.matches
import mct.util.io.readText
import mct.util.unreachable

class Test : SuspendingCliktCommand(name = "test") {
    init {
        subcommands(DataPointerTest(), CommandTest(), PatternDisplay())
    }

    override suspend fun run() = Unit
    override fun help(context: Context) = "Test tools"
}

private class DataPointerTest : BaseCommand(name = "pointer") {
    override fun help(context: Context) = "Test pointer matching"

    val kind by option("--kind", "-k", help = "The kind of inputted pointer").choice("mcjson", "region").required()
    val pattern by option("--pattern", "-p", help = "The file of pattern to match the test").path()
    val noBuiltin by option("--no-builtin", help = "Disable builtin pattern").flag()
    val pointer by argument()

    context(_: Raise<MCTError>)
    override suspend fun App() {
        val extra = pattern.jsonFile<List<DataPointerPattern>>(emptyList())
        val builtin = when (kind) {
            "mcjson" -> BuiltinMCJsonPatterns
            "region" -> BuiltinNbtPatterns
            else -> unreachable
        }
        val patterns = if (noBuiltin) extra else builtin + extra

        val pointer = DataPointer.decodeFromString(pointer)
        val result = pointer.matches(patterns)
        if (result) printlnGreen("true")
        else printlnRed("false")
    }
}

private class CommandTest : BaseCommand(name = "command", help = "Test command pattern") {
    val pattern by withPattern()
    val testedFile by option("--input", "-i", help = "A file which will be used to test the pattern").path().required()

    context(_: Raise<MCTError>)
    override suspend fun App() {
        val testedContent = testedFile.readText()
        val matchResults = extractTextFromCommands(testedContent, pattern).sortedByDescending { it.indices.first }
        val display = matchResults.fold(StringBuilder(testedContent)) { acc, r ->
            acc.setRange(r.indices.first, r.indices.last + 1, (bold + green)(r.content))
            acc
        }
        terminal.println(display)
    }
}

private class PatternDisplay : BaseCommand(name = "pattern", help = "Display the pattern you passed") {
    val pattern by withPattern()
    val compact by option("--compact", "-c", help = "Print the pattern in a compact format").flag()

    context(_: Raise<MCTError>)
    override suspend fun App() {
        if (compact) {
            printlnGreen(pattern.toString())
            return
        }

        val pretty = buildString {
            appendLine(blue("MCT Pattern:"))
            fun newItemLine(name: String, value: String) {
                append(blue(name))
                append(": ")
                append((green + bold)(value))
                appendLine()
            }
            newItemLine("Command", pattern.command.toString())
            newItemLine("Command Data", pattern.commandData.toString())
            newItemLine("Command Component", pattern.commandComponent.toString())
            newItemLine("Command Regex", pattern.commandRegex.toString())
            newItemLine("Nbt", pattern.nbt.toString())
            newItemLine("MCJson", pattern.mcjson.toString())
            newItemLine("Cext", pattern.cext.toString())
        }
        terminal.println(pretty)
    }
}