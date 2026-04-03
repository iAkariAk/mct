package mct.cli.cmd

import arrow.core.raise.Raise
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import kotlinx.coroutines.flow.toList
import mct.DatapackExtractionGroup
import mct.DatapackReplacementGroup
import mct.MCTError
import mct.cli.PrettyJson
import mct.cli.WorkspaceCommand
import mct.cli.jsonFile
import mct.cli.path
import mct.dp.backfillDatapack
import mct.dp.extractFromDatapack
import mct.util.io.writeText
import okio.FileSystem

class Datapack : SuspendingCliktCommand(name = "datapack") {
    override suspend fun run() {
        echo("Some operation about datapacks.")
    }

    init {
        subcommands(ExtractDatapack(), BackfillDatapack())
    }
}

private class ExtractDatapack : WorkspaceCommand(name = "extract") {
    val output by option().path().required()

    context(_: Raise<MCTError>, fs: FileSystem)
    override suspend fun App() {
        val extractions: List<DatapackExtractionGroup> = workspace.extractFromDatapack().toList()
        val result = PrettyJson.encodeToString(extractions)
        output.writeText(result)
    }
}


private class BackfillDatapack : WorkspaceCommand(name = "backfill") {
    val replacementGroups by option("-r").jsonFile<List<DatapackReplacementGroup>>().required()

    context(_: Raise<MCTError>, fs: FileSystem)
    override suspend fun App() {
        workspace.backfillDatapack(replacementGroups)
    }
}
