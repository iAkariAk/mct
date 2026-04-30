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

            output.writeJson(pool)
        }
    }


    private class Unflatten : BaseCommand(
        name = "unflatten",
        help = "Apply translation mapping back into extraction groups"
    ) {

        val input by option("--input", "-i").path().required()
        val mapping by option("--mapping", "-m").path().required()
        val output by option("--output", "-o").path().required()

        context(_: Raise<MCTError>, fs: FileSystem)
        override suspend fun App() {
            val groups = input.jsonFile<List<ExtractionGroup>>()

            val map: TranslationMapping = mapping.jsonFile()

            @Suppress("UNCHECKED_CAST")
            val result: List<ReplacementGroup> = groups.replace(map)

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
        workspace.exportRegionSnbt(output)
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
        val extractionGroups = input.jsonFile<List<ExtractionGroup>>()
        val result = extractionGroups.replaceSimply { replacement }
        output.writeJson(result)
    }
}

private class AITranslate : BaseCommand(
    name = "translate",
    help = "Translate via OpenAI api"
) {
    val input by option("--input", "-i").path().required()
    val output by option("--output", "-o").path().required()
    val termOutput by option("--output-term", "-ot").path().required()
    val term by option("--term").path()
    val apiUrl by option("--openai-api-url", envvar = "OPENAI_URL")
    val model by option("--openai-model", envvar = "OPENAI_MODEL").required()
    val token by option("--openai-token", envvar = "$4=").required()

    context(_: Raise<MCTError>, fs: FileSystem)
    override suspend fun App() {
        val extractionGroups = input.jsonFile<List<ExtractionGroup>>()
        val terms = term.jsonFile<TermTable>(emptySet())
        val translator = OpenAITranslator(apiUrl, token, model, terms, env)

        val dpGroups = extractionGroups.filterIsInstance<DatapackExtractionGroup>()
        val regionGroups = extractionGroups.filterIsInstance<RegionExtractionGroup>()

        suspend fun translate(kind: FormatKind, extractions: List<String>): List<Pair<String, String>> {
            val parsed = extractions.map {
                when (kind) {
                    FormatKind.Json -> MCTJson.decodeFromString<JsonElement>(it).toIR()
                    FormatKind.Snbt -> Snbt.decodeFromString<NbtTag>(it).toIR()
                }.decodeToCompound()
            }

            val compressed = parsed.map { compressCompound(it) }
            val submitted = compressed.mapIndexed { index, string -> string ?: extractions[index] }
            val translated = translator.translate(submitted)
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
            return groups.replace(mapping)
        }

        val translated = translate(dpGroups) + translate(regionGroups)

        output.writeJson(translated)
        termOutput.writeJson(translator.terms)
    }

    private fun compressCompound(compound: TextCompound): String? =
        (compound.takeIf { it.extra.isEmpty() } as? TextCompound.Plain)?.text
}