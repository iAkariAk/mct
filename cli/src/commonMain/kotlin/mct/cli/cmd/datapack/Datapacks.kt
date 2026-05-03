package mct.cli.cmd.datapack

import arrow.core.raise.Raise
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import kotlinx.coroutines.flow.toList
import mct.DatapackReplacementGroup
import mct.ExtractionGroup
import mct.MCTError
import mct.ReplacementGroup
import mct.cli.*
import mct.dp.backfillDatapack
import mct.dp.compile
import mct.dp.extractFromDatapackRaw
import mct.dp.mcfunction.BuiltinMCFPatterns
import mct.dp.mcfunction.ExtractPattern
import mct.dp.mcjson.BuiltinMCJPatterns
import mct.pointer.CustomizedDataPointerPattern
import mct.serializer.MCTJson

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

    val disableMCJFilter by option(
        "--disable-mcjson-filter",
        help = "Disable mcjson filter, extract all strings from JSON files"
    ).flag()


    context(_: Raise<MCTError>)
    override suspend fun App() {
        val mcfPatterns = mcfPatternsPath?.jsonFile<List<ExtractPattern>>()
        val userMcjPatterns = mcjPatternsPath?.readText()?.let {
            MCTJson.decodeFromString<List<CustomizedDataPointerPattern>>(it).map { it.compile() }
        }
        val mcjPatterns = when {
            disableMCJFilter -> null
            userMcjPatterns != null -> BuiltinMCJPatterns + userMcjPatterns
            else -> BuiltinMCJPatterns
        }

        logger.info { "Extracting from datapack..." }
        val extractions: List<ExtractionGroup> =
            workspace.extractFromDatapackRaw(mcfPatterns?.compile() ?: BuiltinMCFPatterns, mcjPatterns).toList()
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
