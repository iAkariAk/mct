package mct.cli.cmd.kits

import arrow.core.raise.Raise
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import mct.MCTError
import mct.cli.WorkspaceCommand
import mct.cli.path
import mct.kit.exportRegionSnbt

class ExportSnbt : WorkspaceCommand(
    name = "export-snbt", help = "A tool helping you extract all nbt from region files"
) {
    val output by option("--output", "-o", help = "The dir where the extracted snbt will be placed").path().required()

    context(_: Raise<MCTError>)
    override suspend fun App() {
        logger.info { "Exporting region SNBT to $output" }
        workspace.exportRegionSnbt(output)
        logger.info { "Done." }
    }
}