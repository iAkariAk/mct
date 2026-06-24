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
import com.github.ajalt.mordant.rendering.TextColors
import mct.MCTError
import mct.cli.*
import mct.command.BuiltinMCFunctionDataPatterns
import mct.command.CommandExtractPattern
import mct.command.extractTextFromCommands
import mct.dp.compile
import mct.dp.mcjson.BuiltinMCJPatterns
import mct.nbt.BuiltinNbtPatterns
import mct.pointer.*
import mct.util.io.readJson
import mct.util.io.readText
import mct.util.unreachable

class Test : SuspendingCliktCommand(name = "test") {
    init {
        subcommands(DataPointerTest(), MCFunctionTest())
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
        val extra = pattern.jsonFile<List<CustomizedDataPointerPattern>>(emptyList()).map { it.compile() }
        val builtin = when (kind) {
            "mcjson" -> BuiltinMCJPatterns
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

private class MCFunctionTest : BaseCommand(name = "mcfunction", help = "Test mcfunction pattern") {
    val mcfunctionPattern by option("--mcfunction-pattern", "-pF", help = "The pattern to match the test").path()
        .required()
    val mcfunctionDataPattern by option(
        "--mcfunction-data-pattern",
        "-pFD",
        help = "The pattern to match the test"
    ).path().required()
    val testedFile by option("--input", "-i", help = "A file which will be used to test the pattern").path().required()
    val noBuiltin by option("--no-builtin", "-N", help = "Disable builtin pattern").flag()

    context(_: Raise<MCTError>)
    override suspend fun App() {
        val testedContent = testedFile.readText()
        val extraMCFunctionPattern = mcfunctionPattern.readJson<List<CommandExtractPattern>>()
        val extraMCFunctionDataPattern = mcfunctionDataPattern.readJson<List<DataPointerPattern>>()
        val combinedMCFunctionPattern = extraMCFunctionPattern.compile(!noBuiltin)
        val combinedMCFunctionDataPattern =
            if (noBuiltin) extraMCFunctionDataPattern else extraMCFunctionDataPattern + BuiltinMCFunctionDataPatterns
        val matchResults = extractTextFromCommands(
            testedContent, mcfPatterns = combinedMCFunctionPattern,
            mcfDataPatterns = combinedMCFunctionDataPattern
        )
        val display = matchResults.fold(StringBuilder(testedContent)) { acc, r ->
            acc.setRange(r.indices.first, r.indices.last - 1, TextColors.green(r.content))
            acc
        }
        terminal.println(display)
    }
}