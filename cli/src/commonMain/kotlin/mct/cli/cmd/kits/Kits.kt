package mct.cli.cmd.kits

import arrow.core.raise.Raise
import com.aallam.openai.api.logging.LogLevel
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.double
import com.github.ajalt.clikt.parameters.types.int
import mct.*
import mct.cli.BaseCommand
import mct.cli.WorkspaceCommand
import mct.cli.jsonFile
import mct.cli.path
import mct.extra.ai.ChatCompletionCall
import mct.extra.ai.TOKEN_COUNT_THRESHOLD
import mct.extra.ai.translator.CustomizedPrompts
import mct.extra.ai.translator.TermTable
import mct.extra.ai.translator.Translator
import mct.extra.ai.translator.translate
import mct.kit.*
import mct.util.io.writeJson
import mct.util.unreachable


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
        name = "unflatten",
        help = "Apply translation mapping back into extraction groups"
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

            @Suppress("UNCHECKED_CAST")
            val result: List<ReplacementGroup> = groups.replace(map)
            logger.info { "Writing ${result.size} replacement groups to $output" }

            output.writeJson(result)
        }
    }
}


private class ExportSnbt : WorkspaceCommand(
    name = "export-snbt",
    help = "A tool helping you extract all nbt from region files"
) {
    val output by option("--output", "-o", help = "The dir where the extracted snbt will be placed").path().required()

    context(_: Raise<MCTError>)
    override suspend fun App() {
        logger.info { "Exporting region SNBT to $output" }
        workspace.exportRegionSnbt(output)
        logger.info { "Done." }
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

    context(_: Raise<MCTError>)
    override suspend fun App() {
        logger.info { "Loading extractions from $input" }
        val extractionGroups = input.jsonFile<List<ExtractionGroup>>()
        logger.info { "Replacing ${extractionGroups.size} groups with $replacement" }
        val result = extractionGroups.replaceSimply { replacement }
        logger.info { "Writing ${result.size} replacement groups to $output" }
        output.writeJson(result)
        logger.info { "Done." }
    }
}

private class AITranslate : BaseCommand(
    name = "translate",
    help = "Translate via OpenAI api"
) {
    val input by option("--input", "-i", help = "The extraction JSON file to translate").path().required()
    val caches by option(
        "--cache-mapping",
        "-cm",
        help = "The cache mapping file exported by the follow `--mapping`. By default"
    ).path()
    val output by option("--output", "-o", help = "The output path for the replacements JSON").path().required()
    val mappingOutput by option("--output-mapping", "-om", help = "The output path for the mapping JSON").path()
        .required()
    val termOutput by option("--output-term", "-ot", help = "The output path for the term table JSON").path().required()
    val term by option("--term", help = "Path to an existing term table JSON file").path()
    val apiUrl by option("--openai-api-url", envvar = "OPENAI_URL", help = "OpenAI compatible API base URL")
    val model by option("--openai-model", envvar = "OPENAI_MODEL", help = "Model name (e.g. gpt-4o)").required()
    val token by option("--openai-token", envvar = "OPENAI_TOKEN", help = "API access token").required()
    val useStreamApi by option(
        "--use-stream-api",
        envvar = "OPENAI_STREAM_API",
        help = "Using streaming API can solve some api empty response, but maybe will slow down translation."
    ).flag(default = false)
    val tokenThreshold by option(
        "--token-treshold",
        envvar = "OPENAI_TOKEN_THRESHOLD",
        help = "The token threshold amount per request."
    ).int().default(TOKEN_COUNT_THRESHOLD)
    val literatureStyle by option(
        "--literature-style",
        envvar = "LITERATURE_STYLE",
        help = "Custom literature style prompt for translation"
    ).default(
        CustomizedPrompts.literatureStyle
    )

    val targetLanguage by option(
        "--target-language",
        envvar = "TARGET_LANGUAGE",
        help = "Target language for translation (e.g. 简体中文, English, 日本語)"
    ).default(
        CustomizedPrompts.targetLanguage
    )

    val temperature by option(
        "--temperature",
        envvar = "OPENAI_TEMPERATURE",
        help = "Temperature for the model (0.0-2.0)"
    ).double()

    val handleGradient by option(
        "--handle-gradient",
        envvar = "HANDLE_GRADIENT",
        help = "Enable aggressive gradient text handling"
    ).flag()

    val enableHttpLogging by option("--http-logging", envvar = "Enable all HTTP logging").flag()

    context(_: Raise<MCTError>)
    override suspend fun App() {
        logger.info { "Loading extractions from $input" }
        val extractionGroups = input.jsonFile<List<ExtractionGroup>>()
        val terms = term.jsonFile<TermTable>(emptySet())
        val caches = caches.jsonFile<Map<String, String>>(emptyMap())
        logger.info { "Loaded ${extractionGroups.size} groups, ${terms.size} existing terms" }

        val translator = context(env) {
            val call = ChatCompletionCall(
                apiUrl = apiUrl,
                token = token,
                model = model,
                useStreamApi = useStreamApi,
                temperature = temperature,
                logLevel = if (enableHttpLogging) LogLevel.All else LogLevel.None,
            )
            Translator(
                call = call,
                customizedPrompts = CustomizedPrompts(
                    literatureStyle = literatureStyle,
                    targetLanguage = targetLanguage,
                    handleGradientAggressively = handleGradient,
                ),
                defaultTerms = terms,
                tokenThreshold = tokenThreshold
            )
        }


        logger.info { "Starting translation..." }
        val mapping = translator.translate(extractionGroups, caches)
        val replacements = extractionGroups.replace(mapping)

        logger.info { "Translation done, ${replacements.size} replacement groups" }

        logger.info { "Writing mapping to $mappingOutput" }
        mappingOutput.writeJson(mapping)
        logger.info { "Writing replacements to $output" }
        output.writeJson(replacements)
        logger.info { "Writing ${translator.terms.size} terms to $termOutput" }
        termOutput.writeJson(translator.terms)
        logger.info { "Done." }
    }
}