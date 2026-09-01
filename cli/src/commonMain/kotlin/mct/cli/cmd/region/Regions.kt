package mct.cli.cmd.region


import arrow.core.raise.Raise
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import kotlinx.coroutines.flow.toList
import mct.MCTError
import mct.cli.WorkspaceCommand
import mct.cli.jsonFile
import mct.cli.path
import mct.cli.withPattern
import mct.model.patch.ExtractionGroup
import mct.model.patch.RegionReplacementGroup
import mct.model.patch.ReplacementGroup
import mct.region.backfillRegion
import mct.region.extractFromRegion
import mct.util.io.writeJson

class Region : SuspendingCliktCommand(name = "region") {
    init {
        subcommands(RegionExtract(), RegionBackfill())
    }

    override suspend fun run() = Unit
    override fun help(context: Context) = "Region operators"
}

private class RegionExtract : WorkspaceCommand(name = "extract") {
    val pattern by withPattern()
    val output by option("--output", "-o", help = "The JSON output path for extracted texts").path().required()

    context(_: Raise<MCTError>)
    override suspend fun App() {


        env.logger.info { "Extracting from region..." }
        val extractions: List<ExtractionGroup> = workspace.extractFromRegion(pattern).toList()
        env.logger.info { "Extracted ${extractions.size} groups, writing to $output" }

        output.writeJson(extractions)
    }
}


private class RegionBackfill : WorkspaceCommand(name = "backfill") {
    val replacementGroupsPath by option(
        "--replacements", "-r",
        help = "The replacements JSON file to apply back to region files"
    ).path().required()

    context(_: Raise<MCTError>)
    override suspend fun App() {
        val replacementGroups =
            replacementGroupsPath.jsonFile<List<ReplacementGroup>>().filterIsInstance<RegionReplacementGroup>()
        workspace.backfillRegion(replacementGroups)
    }
}
