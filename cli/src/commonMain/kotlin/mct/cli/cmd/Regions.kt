package mct.cli.cmd


import arrow.core.raise.Raise
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
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
import mct.pointer.DataPointerPattern
import mct.region.backfillRegion
import mct.region.extractFromRegion
import mct.util.io.writeText
import okio.FileSystem

class Region : SuspendingCliktCommand(name = "region") {
    init {
        subcommands(RegionExtract(), RegionBackfill())
    }

    override suspend fun run() = Unit
    override fun help(context: Context) = "Region operators"
}

private class RegionExtract : WorkspaceCommand(name = "extract") {
    val output by option("--output", "-o").path().required()
    val patternsPath by option("--pattern", "-p").path()

    context(_: Raise<MCTError>, fs: FileSystem)
    override suspend fun App() {
        val patterns = patternsPath.jsonFile<List<DataPointerPattern>>(emptyList())
        val extractions: List<RegionExtractionGroup> = workspace.extractFromRegion(patterns).toList()

        val result = PrettyJson.encodeToString(extractions)
        output.writeText(result)
    }
}


private class RegionBackfill : WorkspaceCommand(name = "backfill") {
    val replacementGroupsPath by option("-r").path().required()

    context(_: Raise<MCTError>, fs: FileSystem)
    override suspend fun App() {
        val replacementGroups = replacementGroupsPath.jsonFile<List<RegionReplacementGroup>>()
        workspace.backfillRegion(replacementGroups)
    }
}
