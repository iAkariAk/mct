package mct.cli.cmd.test

import arrow.core.raise.Raise
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import mct.MCTError
import mct.cli.BaseCommand
import mct.dp.mcjson.BuiltinMCJPatterns
import mct.pointer.DataPointer
import mct.pointer.decodeFromString
import mct.pointer.matches
import mct.region.BuiltinRegionPatterns
import mct.util.unreachable

class Test : SuspendingCliktCommand(name = "test") {
    init {
        subcommands(DataPointerTest())
    }

    override suspend fun run() = Unit
    override fun help(context: Context) = "Test tools"
}

class DataPointerTest : BaseCommand(name = "pointer") {
    override fun help(context: Context) = "Test pointer matching"

    val kind by option("--kind", "-k", help = "The kind of inputted pointer").choice("mcjson", "region").required()
    val pointer by argument()

    context(_: Raise<MCTError>)
    override suspend fun App() {
        val patterns = when (kind) {
            "mcjson" -> BuiltinMCJPatterns
            "region" -> BuiltinRegionPatterns
            else -> unreachable
        }

        val pointer = DataPointer.decodeFromString(pointer)
        println(pointer.matches(patterns))
    }
}
