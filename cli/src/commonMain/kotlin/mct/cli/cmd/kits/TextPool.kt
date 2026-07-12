package mct.cli.cmd.kits

import arrow.core.raise.Raise
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import mct.MCTError
import mct.cli.BaseCommand
import mct.cli.jsonFile
import mct.cli.path
import mct.kit.TranslationMapping
import mct.kit.TranslationPool
import mct.kit.exportIntoPool
import mct.model.patch.*
import mct.util.io.writeJson
import mct.util.unreachable

class TextPool : BaseCommand(
    name = "text-pool", help = "A tool helping you flatten and unflatten these nested extract"
) {
    init {
        subcommands(Flatten(), Unflatten())
    }

    private class Flatten : BaseCommand(
        name = "flatten", help = "Flatten extraction groups into a translation pool"
    ) {
        val input by option("--input", "-i", help = "The extraction JSON file to flatten").path().required()
        val output by option("--output", "-o", help = "The output path for the translation pool JSON").path().required()
        val kind by option(help = "The kind of extractions").choice("datapack", "region").required()
        val simply by option("--simply", help = "Use simple flatten mode without preserving structure").flag()

        context(_: Raise<MCTError>)
        override suspend fun App() {
            val groups = when (kind) {
                "datapack" -> input.jsonFile<List<DatapackExtractionGroup>>()
                "region" -> input.jsonFile<List<RegionExtractionGroup>>()
                else -> unreachable
            }

            logger.info { "Exporting ${groups.size} groups into pool" }
            val pool: TranslationPool = groups.exportIntoPool(simply)
            logger.info { "Pool has ${pool.size} unique texts" }

            output.writeJson(pool)
        }
    }


    private class Unflatten : BaseCommand(
        name = "unflatten", help = "Apply translation mapping back into extraction groups"
    ) {

        val input by option("--input", "-i", help = "The extraction JSON file to unflatten").path().required()
        val mapping by option("--mapping", "-m", help = "The translation mapping JSON file").path().required()
        val output by option("--output", "-o", help = "The output path for the replacement groups JSON").path()
            .required()

        context(_: Raise<MCTError>)
        override suspend fun App() {
            logger.info { "Loading groups from $input" }
            val groups = input.jsonFile<List<ExtractionGroup>>()

            logger.info { "Loading mapping from $mapping" }
            val map: TranslationMapping = mapping.jsonFile()
            logger.info { "Loaded mapping with ${map.size} entries" }

            @Suppress("UNCHECKED_CAST") val result: List<ReplacementGroup> = groups.replace(map)
            logger.info { "Writing ${result.size} replacement groups to $output" }

            output.writeJson(result)
        }
    }
}