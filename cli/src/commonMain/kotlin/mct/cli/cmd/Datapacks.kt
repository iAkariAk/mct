package mct.cli.cmd

import arrow.core.raise.Raise
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import kotlinx.coroutines.flow.toList
import mct.DatapackExtractionGroup
import mct.DatapackReplacementGroup
import mct.MCTError
import mct.cli.WorkspaceCommand
import mct.cli.jsonFile
import mct.cli.path
import mct.dp.backfillDatapack
import mct.dp.compile
import mct.dp.extractFromDatapackRaw
import mct.dp.mcfunction.ExtractPattern
import mct.pointer.DataPointerPattern
import mct.serializer.PrettyJson
import mct.util.io.writeText
import okio.FileSystem
import mct.dp.mcfunction.BuiltinPatterns as MCFBuiltinPatterns
import mct.dp.mcjson.BuiltinPatterns as MCJBuiltinPatterns

class Datapack : SuspendingCliktCommand(name = "datapack") {
    override suspend fun run() = Unit
    override fun help(context: Context) = "Datapack operators"

    init {
        subcommands(ExtractDatapack(), BackfillDatapack())
    }
}

private class ExtractDatapack : WorkspaceCommand(name = "extract") {
    val output by option(help = "The JSON output of extract").path().required()
    val mcfPatternsPath by option(
        "--mcfunction-patterns",
        "-pF",
        help = "Append pattern to filter specified text for mcfunction"
    ).path()
    val mcjPatternsPath by option(
        "--mcjson-patterns",
        "-pJ",
        help = "Append pattern to filter specified text for mcjson"
    ).path()

    val disableMCJFilter by option("--disable-mcjson-filter").flag()


    context(_: Raise<MCTError>, fs: FileSystem)
    override suspend fun App() {
        val mcfPatterns = mcfPatternsPath?.jsonFile<List<ExtractPattern>>()
        val mcjPatterns =
            if (disableMCJFilter) null else mcjPatternsPath.jsonFile<List<DataPointerPattern>>(MCJBuiltinPatterns)

        val extractions: List<DatapackExtractionGroup> =
            workspace.extractFromDatapackRaw(mcfPatterns?.compile() ?: MCFBuiltinPatterns, mcjPatterns).toList()
        val result = PrettyJson.encodeToString(extractions)
        output.writeText(result)
    }
}


private class BackfillDatapack : WorkspaceCommand(name = "backfill") {
    val replacementGroupsPath by option("-r").path().required()

    context(_: Raise<MCTError>, fs: FileSystem)
    override suspend fun App() {
        val replacementGroups = replacementGroupsPath.jsonFile<List<DatapackReplacementGroup>>()
        workspace.backfillDatapack(replacementGroups)
    }
}
