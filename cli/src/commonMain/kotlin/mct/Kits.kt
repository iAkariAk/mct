package mct


import arrow.core.raise.Raise
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import mct.kit.exportRegionSnbt
import okio.Path.Companion.toPath

val KitCmd: SuspendingCliktCommand = Kit()
    .subcommands(ExportSnbt())

private class Kit : SuspendingCliktCommand(name = "kit") {
    override suspend fun run() {
        echo("Some operation about region.")
    }
}

private class ExportSnbt : BaseCommand(name = "export-snbt") {
    val input by option().required()
    val output by option().required()

    context(_: Raise<MCTError>)
    override suspend fun App() {
        val workspace = MCTWorkspace(input.toPath(), env)
        workspace.exportRegionSnbt(output.toPath())
    }
}


