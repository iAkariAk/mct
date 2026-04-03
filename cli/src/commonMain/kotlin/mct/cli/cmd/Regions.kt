package mct.cli.cmd


import arrow.core.raise.Raise
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import kotlinx.coroutines.flow.toList
import mct.MCTError
import mct.RegionExtractionGroup
import mct.RegionReplacementGroup
import mct.cli.PrettyJson
import mct.cli.WorkspaceCommand
import mct.cli.jsonFile
import mct.cli.path
import mct.region.backfillRegion
import mct.region.extractFromRegion
import mct.util.io.writeText
import okio.FileSystem

class Region : SuspendingCliktCommand(name = "region") {
    init {
        subcommands(RegionExtract(), RegionBackfill())
    }

    override suspend fun run() {
        echo("Some operation about region.")
    }
}

private class RegionExtract : WorkspaceCommand(name = "extract") {
    val output by option().path().required()

    context(_: Raise<MCTError>, fs: FileSystem)
    override suspend fun App() {
        val extractions: List<RegionExtractionGroup> = workspace.extractFromRegion().toList()

        val result = PrettyJson.encodeToString(extractions)
        output.writeText(result)
    }
}


private class RegionBackfill : WorkspaceCommand(name = "backfill") {
    val replacementGroups by option("-r").jsonFile<List<RegionReplacementGroup>>().required()

    context(_: Raise<MCTError>, fs: FileSystem)
    override suspend fun App() {
        workspace.backfillRegion(replacementGroups)
    }
}
