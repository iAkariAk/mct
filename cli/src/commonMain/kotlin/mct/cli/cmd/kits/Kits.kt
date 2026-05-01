package mct.cli.cmd.kits

import arrow.core.raise.Raise
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonElement
import mct.*
import mct.cli.*
import mct.kit.*
import mct.serializer.MCTJson
import mct.serializer.Snbt
import mct.text.TextCompound
import mct.text.decodeToCompound
import mct.text.encodeToIR
import mct.util.formatir.toIR
import mct.util.formatir.toJson
import mct.util.translator.OpenAITranslator
import mct.util.translator.TermTable
import mct.util.unreachable
import net.benwoodworth.knbt.NbtTag
import okio.FileSystem


class Kit : SuspendingCliktCommand(name = "kit") {
    init {
        subcommands(ExportSnbt(), ReplaceAll(), TextPool(), AITranslate())
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
        val input by option("--input", "-i", help = "The extraction JSON file to flatten").path().required()
        val output by option("--output", "-o", help = "The output path for the translation pool JSON").path().required()
        val kind by option(help = "The kind of extractions").choice("datapack", "region").required()
        val simply by option("--simply", help = "Use simple flatten mode without preserving structure").flag()

        context(_: Raise<MCTError>, fs: FileSystem)
        override suspend fun App() {
            val groups = when (kind) {
                "datapack" -> input.jsonFile<List<DatapackExtractionGroup>>()
                "region" -> input.jsonFile<List<RegionExtractionGroup>>()
                else -> unreachable
            }

            env.logger.info { "Exporting ${groups.size} groups into pool" }
            val pool: TranslationPool = groups.exportIntoPool(simply)
            env.logger.info { "Pool has ${pool.size} unique texts" }

            output.writeJson(pool)
        }
    }


    private class Unflatten : BaseCommand(
        name = "unflatten",
        help = "Apply translation mapping back into extraction groups"
    ) {

        val input by option("--input", "-i", help = "The extraction JSON file to unflatten").path().required()
        val mapping by option("--mapping", "-m", help = "The translation mapping JSON file").path().required()
        val output by option("--output", "-o", help = "The output path for the replacement groups JSON").path().required()

        context(_: Raise<MCTError>, fs: FileSystem)
        override suspend fun App() {
            env.logger.info { "Loading groups from $input" }
            val groups = input.jsonFile<List<ExtractionGroup>>()

            env.logger.info { "Loading mapping from $mapping" }
            val map: TranslationMapping = mapping.jsonFile()
            env.logger.info { "Loaded mapping with ${map.size} entries" }

            @Suppress("UNCHECKED_CAST")
            val result: List<ReplacementGroup> = groups.replace(map)
            env.logger.info { "Writing ${result.size} replacement groups to $output" }

            output.writeJson(result)
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
        env.logger.info { "Exporting region SNBT to $output" }
        workspace.exportRegionSnbt(output)
        env.logger.info { "Done." }
    }
}

private class ReplaceAll : BaseCommand(name = "replace-all") {
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
    ).default("\"MCT\"")

    context(_: Raise<MCTError>, fs: FileSystem)
    override suspend fun App() {
        env.logger.info { "Loading extractions from $input" }
        val extractionGroups = input.jsonFile<List<ExtractionGroup>>()
        env.logger.info { "Replacing ${extractionGroups.size} groups with $replacement" }
        val result = extractionGroups.replaceSimply { replacement }
        env.logger.info { "Writing ${result.size} replacement groups to $output" }
        output.writeJson(result)
        env.logger.info { "Done." }
    }
}

private class AITranslate : BaseCommand(
    name = "translate",
    help = "Translate via OpenAI api"
) {
    val input by option("--input", "-i", help = "The extraction JSON file to translate").path().required()
    val output by option("--output", "-o", help = "The output path for the replacements JSON").path().required()
    val termOutput by option("--output-term", "-ot", help = "The output path for the term table JSON").path().required()
    val term by option("--term", help = "Path to an existing term table JSON file").path()
    val apiUrl by option("--openai-api-url", envvar = "OPENAI_URL", help = "OpenAI compatible API base URL")
    val model by option("--openai-model", envvar = "OPENAI_MODEL", help = "Model name (e.g. gpt-4o)").required()
    val token by option("--openai-token", envvar = "OPENAI_TOKEN", help = "API access token").required()

    context(_: Raise<MCTError>, fs: FileSystem)
    override suspend fun App() {
        env.logger.info { "Loading extractions from $input" }
        val extractionGroups = input.jsonFile<List<ExtractionGroup>>()
        val terms = term.jsonFile<TermTable>(emptySet())
        env.logger.info { "Loaded ${extractionGroups.size} groups, ${terms.size} existing terms" }

        val translator = OpenAITranslator(apiUrl, token, model, terms, env)

        val dpGroups = extractionGroups.filterIsInstance<DatapackExtractionGroup>()
        val regionGroups = extractionGroups.filterIsInstance<RegionExtractionGroup>()
        env.logger.info { "Groups: ${dpGroups.size} datapack, ${regionGroups.size} region" }

        suspend fun translate(kind: FormatKind, extractions: List<String>): List<Pair<String, String>> {
            env.logger.debug { "Parsing ${extractions.size} $kind extractions" }
            val parsed = extractions.map {
                when (kind) {
                    FormatKind.Json -> MCTJson.decodeFromString<JsonElement>(it).toIR()
                    FormatKind.Snbt -> Snbt.decodeFromString<NbtTag>(it).toIR()
                }.decodeToCompound()
            }

            env.logger.debug { "Compressing ${parsed.size} parsed compounds" }
            val compressed = parsed.map { compressCompound(it) }
            val submitted = compressed.mapIndexed { index, string -> string ?: extractions[index] }
            env.logger.info { "Translating ${submitted.size} texts ($kind)" }
            val translated = translator.translate(submitted)
            env.logger.debug { "Decompressing ${translated.size} translated texts" }
            val decompressed = compressed.mapIndexed { index, string ->
                val translated = translated.getOrNull(index) ?: submitted[index]
                if (string != null) {
                    null
                } else {
                    MCTJson.encodeToString(
                        (parsed.getOrNull(index) as? TextCompound.Plain)?.copy(text = translated)?.encodeToIR()?.toJson()
                    )
                } ?: translated
            }
            return extractions.zip(decompressed)
        }

        suspend fun translate(groups: List<ExtractionGroup>): List<ReplacementGroup> {
            if (groups.isEmpty()) return emptyList()
            env.logger.debug { "Grouping ${groups.flatMap { it.extractions }.size} extractions by format" }
            val extractions = groups.flatMap { it.extractions }.groupBy {
                when (it) {
                    is DatapackExtraction -> FormatKind.Json
                    is RegionExtraction -> it.kind
                }
            }
            val mapping = extractions.flatMap { (kind, extractions) ->
                translate(
                    kind,
                    extractions.asSequence().mapNotNull { it.content.takeIf { it.isNotBlank() } }.distinct().toList()
                )
            }.toMap()
            env.logger.info { "Built mapping with ${mapping.size} entries" }
            return groups.replace(mapping)
        }

        env.logger.info { "Starting translation..." }
        val translated = translate(dpGroups) + translate(regionGroups)
        env.logger.info { "Translation done, ${translated.size} replacement groups" }

        env.logger.info { "Writing replacements to $output" }
        output.writeJson(translated)
        env.logger.info { "Writing ${translator.terms.size} terms to $termOutput" }
        termOutput.writeJson(translator.terms)
        env.logger.info { "Done." }
    }

    private fun compressCompound(compound: TextCompound): String? =
        (compound.takeIf { it.extra.isEmpty() } as? TextCompound.Plain)?.text
}