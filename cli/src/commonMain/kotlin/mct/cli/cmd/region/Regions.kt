package mct.cli.cmd.region


import arrow.core.raise.Raise
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import kotlinx.coroutines.flow.toList
import mct.ExtractionGroup
import mct.MCTError
import mct.RegionReplacementGroup
import mct.ReplacementGroup
import mct.cli.WorkspaceCommand
import mct.cli.jsonFile
import mct.cli.path
import mct.cli.writeJson
import mct.pointer.DataPointerPattern
import mct.region.BuiltinPatterns
import mct.region.backfillRegion
import mct.region.extractFromRegion
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

    val disableFilter by option("--disable-filter").flag()

    context(_: Raise<MCTError>, fs: FileSystem)
    override suspend fun App() {
        val patterns = if (disableFilter) null else patternsPath.jsonFile<List<DataPointerPattern>>(BuiltinPatterns)
        val extractions: List<ExtractionGroup> = workspace.extractFromRegion(patterns).toList()

        output.writeJson(extractions)
    }
}


private class RegionBackfill : WorkspaceCommand(name = "backfill") {
    val replacementGroupsPath by option("-r").path().required()

    context(_: Raise<MCTError>, fs: FileSystem)
    override suspend fun App() {
        val replacementGroups =
            replacementGroupsPath.jsonFile<List<ReplacementGroup>>().filterIsInstance<RegionReplacementGroup>()
        workspace.backfillRegion(replacementGroups)
    }
}
