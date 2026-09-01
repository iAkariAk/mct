package mct.cli.cmd.cext


import arrow.core.raise.Raise
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import kotlinx.coroutines.flow.toList
import mct.MCTError
import mct.cext.backfillCext
import mct.cext.extractByCext
import mct.cli.WorkspaceCommand
import mct.cli.enforceNotNull
import mct.cli.path
import mct.cli.withPattern
import mct.model.patch.CextReplacementGroup
import mct.model.patch.ExtractionGroup
import mct.model.patch.ReplacementGroup
import mct.util.io.readJson
import mct.util.io.writeJson

class Cext : SuspendingCliktCommand(name = "cext") {
    init {
        subcommands(CextExtract(), CextBackfill())
    }

    override suspend fun run() = Unit
    override fun help(context: Context) = "Customize your extractor"
}

private class CextExtract : WorkspaceCommand(name = "extract") {
    val pattern by withPattern()
    val output by option("--output", "-o", help = "The JSON output path for extracted texts").path().required()

    context(_: Raise<MCTError>)
    override suspend fun App() {
        enforceNotNull(pattern.cext) { "Please config cext patterns" }

        logger.info { "Extracting texts by cext" }
        val extractions = workspace.extractByCext(pattern).toList()
        logger.info { "Extracted ${extractions.size} groups" }
        output.writeJson<List<ExtractionGroup>>(extractions)
    }
}


private class CextBackfill : WorkspaceCommand(name = "backfill") {
    val replacementGroupsPath by option(
        "--replacements", "-r",
        help = "The replacements JSON file"
    ).path().required()

    context(_: Raise<MCTError>)
    override suspend fun App() {
        val replacementGroups =
            replacementGroupsPath.readJson<List<ReplacementGroup>>().filterIsInstance<CextReplacementGroup>()
        if (replacementGroups.isEmpty()) {
            logger.info { "No replacement of type CextReplacementGroup was found" }
        }
        workspace.backfillCext(replacementGroups)
    }
}
