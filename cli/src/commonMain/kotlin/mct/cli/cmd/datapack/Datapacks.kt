package mct.cli.cmd.datapack

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
import mct.dp.backfillDatapack
import mct.dp.extractFromDatapack
import mct.model.patch.DatapackReplacementGroup
import mct.model.patch.ExtractionGroup
import mct.model.patch.ReplacementGroup
import mct.util.io.writeJson

class Datapack : SuspendingCliktCommand(name = "datapack") {
    override suspend fun run() = Unit
    override fun help(context: Context) = "Datapack operators"

    init {
        subcommands(ExtractDatapack(), BackfillDatapack())
    }
}

private class ExtractDatapack : WorkspaceCommand(name = "extract") {
    val pattern by withPattern()
    val output by option("--output", "-o", help = "The JSON output path for extracted texts").path().required()

    context(_: Raise<MCTError>)
    override suspend fun App() {
        logger.info { "Extracting from datapack..." }
        val extractions: List<ExtractionGroup> =
            workspace.extractFromDatapack(pattern).toList()
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
