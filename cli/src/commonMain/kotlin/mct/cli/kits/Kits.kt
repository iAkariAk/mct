package mct.cli.kits


import arrow.core.raise.Raise
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import mct.DatapackExtractionGroup
import mct.MCTError
import mct.RegionExtractionGroup
import mct.cli.BaseCommand
import mct.cli.WorkspaceCommand
import mct.cli.panic
import mct.cli.path
import mct.kit.exportRegionSnbt
import mct.kit.replaceSimply
import mct.serializer.MCTJson
import mct.util.io.readText
import mct.util.io.writeText
import okio.FileSystem

val KitCmd: SuspendingCliktCommand = Kit()
    .subcommands(ExportSnbt(), Ciallo())

private class Kit : SuspendingCliktCommand(name = "kit") {
    override suspend fun run() {
        echo("Some operation about region.")
    }
}

private class ExportSnbt : WorkspaceCommand(name = "export-snbt") {
    val output by option().path().required()

    context(_: Raise<MCTError>, fs: FileSystem)
    override suspend fun App() {
        workspace.exportRegionSnbt(output)
    }
}

private class Ciallo : BaseCommand(name = "ciallo") {
    val input by option("--input", "-i").path().required()
    val output by option("--output", "-o").path().required()
    val kind by option().required()

    context(_: Raise<MCTError>, fs: FileSystem)
    override suspend fun App() {
        when (kind) {
            "datapack" -> {
                val extractionGroups = MCTJson.decodeFromString<List<DatapackExtractionGroup>>(input.readText())
                val cialloized = extractionGroups.replaceSimply { str ->
                    "{CIALLO powered by MCT}"
                }


                output.writeText(MCTJson.encodeToString(cialloized))
            }

            "region" -> {
                val extractionGroups = MCTJson.decodeFromString<List<RegionExtractionGroup>>(input.readText())
                val cialloized = extractionGroups.replaceSimply { str ->
                    "{CIALLO powered by MCT}"
                }


                output.writeText(MCTJson.encodeToString(cialloized))
            }

            else -> panic("The argument should be datapack or region")
        }

    }
}

