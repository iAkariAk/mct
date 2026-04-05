package mct.cli.cmd

import arrow.core.raise.Raise
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
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
import mct.dp.mcfunction.ExtractPattern
import mct.pointer.DataPointerPattern
import mct.util.io.writeText
import okio.FileSystem

class Datapack : SuspendingCliktCommand(name = "datapack") {
    override suspend fun run() = Unit
    override fun help(context: Context) = "Datapack operators"

    init {
        subcommands(ExtractDatapack(), BackfillDatapack())
    }
}

private class ExtractDatapack : WorkspaceCommand(name = "extract") {
    val output by option(help = "The JSON output of extract").path().required()
    val mcfPatterns by option("--mcfunction-patterns", "-pF", help = "Append pattern to filter specified text for mcfunction").jsonFile<List<ExtractPattern>>().default(emptyList())
    val mcjPatterns by option("--mcjson-patterns", "-pJ", help = "Append pattern to filter specified text for mcjson").jsonFile<List<DataPointerPattern>>().default(emptyList())

    context(_: Raise<MCTError>, fs: FileSystem)
    override suspend fun App() {
        val extractions: List<DatapackExtractionGroup> =
            workspace.extractFromDatapack(mcfPatterns, mcjPatterns).toList()
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
