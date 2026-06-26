package mct.cli.cmd.region


import arrow.core.raise.Raise
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import kotlinx.coroutines.flow.toList
import mct.MCTError
import mct.MCTPattern
import mct.cli.WorkspaceCommand
import mct.cli.jsonFile
import mct.cli.path
import mct.command.BuiltinCommandDataPatterns
import mct.command.BuiltinCommandPatterns
import mct.command.CommandExtractPattern
import mct.command.CommandRegexPattern
import mct.dp.compile
import mct.model.patch.ExtractionGroup
import mct.model.patch.RegionReplacementGroup
import mct.model.patch.ReplacementGroup
import mct.nbt.BuiltinNbtPatterns
import mct.pointer.CustomizedDataPointerPattern
import mct.region.backfillRegion
import mct.region.extractFromRegion
import mct.serializer.MCTJson
import mct.util.io.readText
import mct.util.io.writeJson

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
    val commandPatternsPath by option(
        "--command-patterns",
        "-pF",
        help = "Append patterns to filter specified text for command"
    ).path()
    val commandDataPatternsPath by option(
        "--command-data-patterns",
        "-pD",
        help = "Append patterns to filter command snbt args"
    ).path()
    val disableCommandDataFilter by option(
        "--disable-command-data-filter",
        help = "Disable command snbt arg filter, extract all strings"
    ).flag()
    val commandRegexPatternsPath by option(
        "--command-regex-patterns",
        "-pR",
        help = "Append regex patterns to extract text from command"
    ).path()

    context(_: Raise<MCTError>)
    override suspend fun App() {
        val userPatterns = patternsPath?.readText()?.let {
            MCTJson.decodeFromString<List<CustomizedDataPointerPattern>>(it).map { it.compile() }
        }
        val patterns = when {
            disableFilter -> null
            userPatterns != null -> BuiltinNbtPatterns.toList() + userPatterns
            else -> BuiltinNbtPatterns.toList()
        }

        val commandPatterns = commandPatternsPath?.jsonFile<List<CommandExtractPattern>>()
        val userCommandDataPatterns = commandDataPatternsPath?.readText()?.let {
            MCTJson.decodeFromString<List<CustomizedDataPointerPattern>>(it).map { it.compile() }
        }
        val commandDataPatterns = when {
            disableCommandDataFilter -> null
            userCommandDataPatterns != null -> BuiltinCommandDataPatterns + userCommandDataPatterns
            else -> BuiltinCommandDataPatterns
        }

        val commandRegexPatterns = commandRegexPatternsPath?.jsonFile<List<CommandRegexPattern>>() ?: emptyList()

        env.logger.info { "Extracting from region..." }
        val extractions: List<ExtractionGroup> = workspace.extractFromRegion(MCTPattern(
            nbt = patterns,
            command = commandPatterns?.compile() ?: BuiltinCommandPatterns,
            commandData = commandDataPatterns,
            commandRegex = commandRegexPatterns
        )).toList()
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
