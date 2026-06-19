package mct.cli.cmd.test

import arrow.core.raise.Raise
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import mct.MCTError
import mct.cli.BaseCommand
import mct.cli.jsonFile
import mct.cli.path
import mct.dp.mcjson.BuiltinMCJPatterns
import mct.nbt.BuiltinNbtPatterns
import mct.pointer.DataPointer
import mct.pointer.DataPointerPattern
import mct.pointer.decodeFromString
import mct.pointer.matches
import mct.util.unreachable

class Test : SuspendingCliktCommand(name = "test") {
    init {
        subcommands(DataPointerTest())
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
        val builtin =  when (kind) {
            "mcjson" -> BuiltinMCJPatterns
            "region" -> BuiltinNbtPatterns
            else -> unreachable
        }
        val patterns = if (noBuiltin) extra else builtin + extra

        val pointer = DataPointer.decodeFromString(pointer)
        println(pointer.matches(patterns))
    }
}
