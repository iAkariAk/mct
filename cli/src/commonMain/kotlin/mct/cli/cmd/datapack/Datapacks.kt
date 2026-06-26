package mct.cli.cmd.datapack

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
import mct.dp.backfillDatapack
import mct.dp.compile
import mct.dp.extractFromDatapack
import mct.dp.mcjson.BuiltinMCJPatterns
import mct.model.patch.DatapackReplacementGroup
import mct.model.patch.ExtractionGroup
import mct.model.patch.ReplacementGroup
import mct.pointer.CustomizedDataPointerPattern
import mct.serializer.MCTJson
import mct.util.io.readText
import mct.util.io.writeJson

class Datapack : SuspendingCliktCommand(name = "datapack") {
    override suspend fun run() = Unit
    override fun help(context: Context) = "Datapack operators"

    init {
        subcommands(ExtractDatapack(), BackfillDatapack())
    }
}

private class ExtractDatapack : WorkspaceCommand(name = "extract") {
    val output by option("--output", "-o", help = "The JSON output path for extracted texts").path().required()
    val commandPatternsPath by option(
        "--command-patterns",
        "-pF",
        help = "Append patterns to filter specified text for command"
    ).path()
    val mcjPatternsPath by option(
        "--mcjson-patterns",
        "-pJ",
        help = "Append patterns to filter specified text for mcjson"
    ).path()

    val commandDataPatternsPath by option(
        "--command-data-patterns",
        "-pD",
        help = "Append patterns to filter command snbt args"
    ).path()
    val disableMCJFilter by option(
        "--disable-mcjson-filter",
        help = "Disable mcjson filter, extract all strings from JSON files"
    ).flag()
    val disableCommandDataFilter by option(
        "--disable-command-data-filter",
        help = "Disable command snbt arg filter, extract all strings from JSON files"
    ).flag()
    val commandRegexPatternsPath by option(
        "--command-regex-patterns",
        "-pR",
        help = "Append regex patterns to extract text from command"
    ).path()

    context(_: Raise<MCTError>)
    override suspend fun App() {
        val commandPatterns = commandPatternsPath?.jsonFile<List<CommandExtractPattern>>()
        val userMcjPatterns = mcjPatternsPath?.readText()?.let {
            MCTJson.decodeFromString<List<CustomizedDataPointerPattern>>(it).map { it.compile() }
        }
        val userCommandDataPatterns = commandDataPatternsPath?.readText()?.let {
            MCTJson.decodeFromString<List<CustomizedDataPointerPattern>>(it).map { it.compile() }
        }
        val mcjPatterns = when {
            disableMCJFilter -> null
            userMcjPatterns != null -> BuiltinMCJPatterns + userMcjPatterns
            else -> BuiltinMCJPatterns
        }
        val commandDataPatterns = when {
            disableCommandDataFilter -> null
            userCommandDataPatterns != null -> BuiltinCommandDataPatterns + userCommandDataPatterns
            else -> BuiltinCommandDataPatterns
        }

        val commandRegexPatterns = commandRegexPatternsPath?.jsonFile<List<CommandRegexPattern>>() ?: emptyList()

        logger.info { "Extracting from datapack..." }
        val extractions: List<ExtractionGroup> =
            workspace.extractFromDatapack(
                MCTPattern(
                    command = commandPatterns?.compile() ?: BuiltinCommandPatterns,
                    commandData = commandDataPatterns,
                    mcjson = mcjPatterns,
                    commandRegex = commandRegexPatterns
                )
            ).toList()
        logger.info { "Extracted ${extractions.size} groups, writing to $output" }
        output.writeJson(extractions)
    }
}


private class BackfillDatapack : WorkspaceCommand(name = "backfill") {
    val replacementGroupsPath by option(
        "--replacements", "-r",
        help = "The replacements JSON file to apply back to datapack files"
    ).path().required()

    context(_: Raise<MCTError>)
    override suspend fun App() {
        logger.info { "Backfilling datapack..." }
        val replacementGroups =
            replacementGroupsPath.jsonFile<List<ReplacementGroup>>().filterIsInstance<DatapackReplacementGroup>()
        logger.info { "Loaded ${replacementGroups.size} datapack replacement groups" }
        workspace.backfillDatapack(replacementGroups)
        logger.info { "Datapack backfill complete" }
    }
}
