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
import mct.command.BuiltinMCFPatterns
import mct.command.BuiltinMCFunctionDataPatterns
import mct.command.CommandExtractPattern
import mct.command.RegexPattern
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
    val mcfPatternsPath by option(
        "--mcfunction-patterns",
        "-pF",
        help = "Append patterns to filter specified text for mcfunction"
    ).path()
    val mcjPatternsPath by option(
        "--mcjson-patterns",
        "-pJ",
        help = "Append patterns to filter specified text for mcjson"
    ).path()

    val mcfDataPatternsPath by option(
        "--mcfunction-data-patterns",
        "-pD",
        help = "Append patterns to filter mcfunction snbt args"
    ).path()
    val disableMCJFilter by option(
        "--disable-mcjson-filter",
        help = "Disable mcjson filter, extract all strings from JSON files"
    ).flag()
    val disableMCFDFilter by option(
        "--disable-mcfunction-data-filter",
        help = "Disable mcfunction snbt arg filter, extract all strings from JSON files"
    ).flag()
    val mcfunctionRegexPatternsPath by option(
        "--mcfunction-regex-patterns",
        "-pR",
        help = "Append regex patterns to extract text from mcfunction"
    ).path()

    context(_: Raise<MCTError>)
    override suspend fun App() {
        val mcfPatterns = mcfPatternsPath?.jsonFile<List<CommandExtractPattern>>()
        val userMcjPatterns = mcjPatternsPath?.readText()?.let {
            MCTJson.decodeFromString<List<CustomizedDataPointerPattern>>(it).map { it.compile() }
        }
        val userMcfDataPatterns = mcfDataPatternsPath?.readText()?.let {
            MCTJson.decodeFromString<List<CustomizedDataPointerPattern>>(it).map { it.compile() }
        }
        val mcjPatterns = when {
            disableMCJFilter -> null
            userMcjPatterns != null -> BuiltinMCJPatterns + userMcjPatterns
            else -> BuiltinMCJPatterns
        }
        val mcfDataPatterns = when {
            disableMCFDFilter -> null
            userMcfDataPatterns != null -> BuiltinMCFunctionDataPatterns + userMcfDataPatterns
            else -> BuiltinMCFunctionDataPatterns
        }

        val mcfunctionRegexPatterns = mcfunctionRegexPatternsPath?.jsonFile<List<RegexPattern>>() ?: emptyList()

        logger.info { "Extracting from datapack..." }
        val extractions: List<ExtractionGroup> =
            workspace.extractFromDatapack(MCTPattern(
                mcfunction = mcfPatterns?.compile() ?: BuiltinMCFPatterns,
                mcfunctionData = mcfDataPatterns,
                mcjson = mcjPatterns,
                mcfunctionRegex = mcfunctionRegexPatterns
            )).toList()
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
