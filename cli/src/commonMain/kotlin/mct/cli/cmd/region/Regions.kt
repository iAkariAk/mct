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
import mct.cli.*
import mct.pointer.CustomizedDataPointerPattern
import mct.region.BuiltinRegionPatterns
import mct.region.backfillRegion
import mct.region.extractFromRegion
import mct.serializer.MCTJson

class Region : SuspendingCliktCommand(name = "region") {
    init {
        subcommands(RegionExtract(), RegionBackfill())
    }

    override suspend fun run() = Unit
    override fun help(context: Context) = "Region operators"
}

private class RegionExtract : WorkspaceCommand(name = "extract") {
    val output by option("--output", "-o", help = "The JSON output path for extracted texts").path().required()
    val patternsPath by option("--pattern", "-p", help = "Custom region filter patterns JSON file").path()

    val disableFilter by option("--disable-filter", help = "Disable built-in filter, extract all strings").flag()

    context(_: Raise<MCTError>)
    override suspend fun App() {
        val userPatterns = patternsPath?.readText()?.let {
            MCTJson.decodeFromString<List<CustomizedDataPointerPattern>>(it).map { it.compile() }
        }
        val patterns = when {
            disableFilter -> null
            userPatterns != null -> BuiltinRegionPatterns.toList() + userPatterns
            else -> BuiltinRegionPatterns.toList()
        }
        env.logger.info { "Extracting from region..." }
        val extractions: List<ExtractionGroup> = workspace.extractFromRegion(patterns).toList()
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
