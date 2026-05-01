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
import mct.cli.WorkspaceCommand
import mct.cli.jsonFile
import mct.cli.path
import mct.cli.writeJson
import mct.dp.backfillDatapack
import mct.dp.compile
import mct.dp.extractFromDatapackRaw
import mct.dp.mcfunction.ExtractPattern
import mct.pointer.CustomizedDataPointerPattern
import mct.serializer.MCTJson
import mct.util.io.readText
import okio.FileSystem
import mct.dp.mcfunction.BuiltinMCFPatterns as MCFBuiltinPatterns
import mct.dp.mcjson.BuiltinMCJPatterns as MCJBuiltinPatterns

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


    context(_: Raise<MCTError>, fs: FileSystem)
    override suspend fun App() {
        val mcfPatterns = mcfPatternsPath?.jsonFile<List<ExtractPattern>>()
        val userMcjPatterns = mcjPatternsPath?.readText()?.let {
            MCTJson.decodeFromString<List<CustomizedDataPointerPattern>>(it).map { it.compile() }
        }
        val mcjPatterns = when {
            disableMCJFilter -> null
            userMcjPatterns != null -> MCJBuiltinPatterns + userMcjPatterns
            else -> MCJBuiltinPatterns
        }

        val extractions: List<ExtractionGroup> =
            workspace.extractFromDatapackRaw(mcfPatterns?.compile() ?: MCFBuiltinPatterns, mcjPatterns).toList()
        output.writeJson(extractions)
    }
}


private class BackfillDatapack : WorkspaceCommand(name = "backfill") {
    val replacementGroupsPath by option(
        "--replacements", "-r",
        help = "The replacements JSON file to apply back to datapack files"
    ).path().required()

    context(_: Raise<MCTError>, fs: FileSystem)
    override suspend fun App() {
        val replacementGroups =
            replacementGroupsPath.jsonFile<List<ReplacementGroup>>().filterIsInstance<DatapackReplacementGroup>()
        workspace.backfillDatapack(replacementGroups)
    }
}
