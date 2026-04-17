package mct.cli.cmd

import arrow.core.raise.Raise
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import mct.*
import mct.cli.BaseCommand
import mct.cli.WorkspaceCommand
import mct.cli.jsonFile
import mct.cli.path
import mct.kit.*
import mct.serializer.MCTJson
import mct.serializer.PrettyJson
import mct.util.io.readText
import mct.util.io.writeText
import mct.util.unreachable
import okio.FileSystem


class Kit : SuspendingCliktCommand(name = "kit") {
    init {
        subcommands(ExportSnbt(), Ciallo(), TextPool())
    }

    override fun help(context: Context) = "Some helpful tool"

    override suspend fun run() = Unit
}

private class TextPool : BaseCommand(
    name = "text-pool",
    help = "A tool helping you flatten and unflatten these nested extract"
) {
    init {
        subcommands(Flatten(), Unflatten())
    }


    private class Flatten : BaseCommand(
        name = "flatten",
        help = "Flatten extraction groups into a translation pool"
    ) {
        val input by option("--input", "-i").path().required()
        val output by option("--output", "-o").path().required()
        val kind by option(help = "The kind of extractions").choice("datapack", "region").required()
        val simply by option("--simply").flag()

        context(_: Raise<MCTError>, fs: FileSystem)
        override suspend fun App() {
            val groups = when (kind) {
                "datapack" -> input.jsonFile<List<DatapackExtractionGroup>>()
                "region" -> input.jsonFile<List<RegionExtractionGroup>>()
                else -> unreachable
            }

            val pool: TranslationPool = groups.exportIntoPool(simply)

            output.writeText(
                PrettyJson.encodeToString(pool)
            )
        }
    }


    private class Unflatten : BaseCommand(
        name = "unflatten",
        help = "Apply translation mapping back into extraction groups"
    ) {

        val input by option("--input", "-i").path().required()
        val mapping by option("--mapping", "-m").path().required()
        val output by option("--output", "-o").path().required()
        val kind by option(help = "The kind of extractions").choice("datapack", "region").required()

        context(_: Raise<MCTError>, fs: FileSystem)
        override suspend fun App() {
            val groups = when (kind) {
                "datapack" -> input.jsonFile<List<DatapackExtractionGroup>>()
                "region" -> input.jsonFile<List<RegionExtractionGroup>>()
                else -> unreachable
            }

            val map: TranslationMapping = mapping.jsonFile()

            @Suppress("UNCHECKED_CAST")
            val result: List<ReplacementGroup<*>> = groups.replace(map)

            @Suppress("UNCHECKED_CAST")
            when (kind) {
                "datapack" -> PrettyJson.encodeToString(result as List<DatapackReplacementGroup>)
                "region" -> PrettyJson.encodeToString(result as List<RegionReplacementGroup>)
                else -> unreachable
            }.let { output.writeText(it) }
        }
    }
}


private class ExportSnbt : WorkspaceCommand(
    name = "export-snbt",
    help = "A tool helping you extract all nbt from region files"
) {
    val output by option("--output", "-o", help = "The dir where the extracted snbt will be placed").path().required()

    context(_: Raise<MCTError>, fs: FileSystem)
    override suspend fun App() {
        workspace.exportRegionSnbt(output)
    }
}

private class Ciallo : BaseCommand(name = "ciallo") {
    val input by option(
        "--input",
        "-i",
        help = "The path to what you want to replace extractions with a specified string"
    ).path().required()
    val output by option("--output", "-o", help = "The output path").path().required()
    val replacement by option(
        "--replacement",
        "-r",
        help = "The replacement which will replace extraction"
    ).default("{CIALLO powered by MCT}")
    val kind by option(help = "The kind of extractions").choice("datapack", "region").required()

    context(_: Raise<MCTError>, fs: FileSystem)
    override suspend fun App() {
        when (kind) {
            "datapack" -> {
                val extractionGroups = MCTJson.decodeFromString<List<DatapackExtractionGroup>>(input.readText())
                val cialloized = extractionGroups.replaceSimply { replacement }
                output.writeText(MCTJson.encodeToString(cialloized))
            }

            "region" -> {
                val extractionGroups = MCTJson.decodeFromString<List<RegionExtractionGroup>>(input.readText())
                val cialloized = extractionGroups.replaceSimply { replacement }
                output.writeText(MCTJson.encodeToString(cialloized))
            }

            else -> unreachable
        }

    }
}

