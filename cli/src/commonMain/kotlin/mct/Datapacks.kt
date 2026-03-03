package mct

import arrow.core.raise.Raise
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import kotlinx.coroutines.flow.toList
import mct.dp.extractFromDatapack
import okio.Path.Companion.toPath

val DatapackCmd: SuspendingCliktCommand = Datapack()
    .subcommands(ExtractDatapack())

private class Datapack : SuspendingCliktCommand(name = "db") {
    override suspend fun run() {
        echo("Some operation about datapacks.")
    }
}

private class ExtractDatapack : BaseCommand(name = "extract") {
    val input by option().required()
    val output by option().required()

    context(_: Raise<MCTError>)
    override suspend fun App() {
        val workspace = MCTWorkspace(input.toPath(), env)
        val extractions = workspace.extractFromDatapack().toList()
        env.fs.write(output.toPath()) {
            val result = PrettyJson.encodeToString(extractions)
            writeUtf8(result)
        }
    }
}


