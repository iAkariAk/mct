package mct.cli.cmd.kits

import arrow.core.raise.Raise
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import mct.MCTError
import mct.VersionHolder
import mct.cli.BaseCommand
import mct.cli.jsonFile
import mct.cli.path
import mct.model.patch.ExtractionGroup
import mct.model.patch.replaceSimply
import mct.util.io.writeJson

class ReplaceAllCommand : BaseCommand(name = "replace-all") {
    val input by option(
        "--input", "-i", help = "The path to what you want to replace extractions with a specified string"
    ).path().required()
    val output by option("--output", "-o", help = "The output path").path().required()
    val replacement by option(
        "--replacement", "-r", help = "The replacement which will replace extraction"
    ).default("\"MCT\"")
    val dataVersion by option("--data-version", help = "The Minecraft data version").int().default(Int.MAX_VALUE)

    context(_: Raise<MCTError>)
    override suspend fun App() {
        context(VersionHolder.of(dataVersion)) {
            logger.info { "Loading extractions from $input" }
            val extractionGroups = input.jsonFile<List<ExtractionGroup>>()
            logger.info { "Replacing ${extractionGroups.size} groups with $replacement" }
            val result = extractionGroups.replaceSimply { replacement }
            logger.info { "Writing ${result.size} replacement groups to $output" }
            output.writeJson(result)
            logger.info { "Done." }
        }
    }
}