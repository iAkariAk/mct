package mct.cli.cmd.kits

import arrow.core.raise.Raise
import com.aallam.openai.api.logging.LogLevel
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.double
import com.github.ajalt.clikt.parameters.types.int
import mct.MCTError
import mct.cli.BaseCommand
import mct.cli.NotifierHooks
import mct.cli.jsonFile
import mct.cli.path
import mct.extra.ai.AiSign
import mct.extra.ai.ChatCompletionCall
import mct.extra.ai.ChatCompletionCallError
import mct.extra.ai.TOKEN_COUNT_THRESHOLD
import mct.extra.ai.translator.*
import mct.kit.TranslationPool
import mct.model.patch.ExtractionGroup
import mct.model.patch.replace
import mct.util.io.readJson
import mct.util.io.writeJson

open class AICommand(
    name: String? = null, help: String? = null,
) : BaseCommand(name = name, help = help) {
    val apiUrl by option("--openai-api-url", envvar = "OPENAI_URL", help = "OpenAI compatible API base URL")
    val model by option("--openai-model", envvar = "OPENAI_MODEL", help = "Model name (e.g. gpt-4o)").required()
    val token by option("--openai-token", envvar = "OPENAI_TOKEN", help = "API access token").required()
    val useStreamApi by option(
        "--use-stream-api",
        envvar = "OPENAI_STREAM_API",
        help = "Using streaming API can solve some api empty response, but maybe will slow down translation."
    ).flag(default = false)
    val tokenThreshold by option(
        "--token-treshold", envvar = "OPENAI_TOKEN_THRESHOLD", help = "The token threshold amount per request."
    ).int().default(TOKEN_COUNT_THRESHOLD)

    val enableHttpLogging by option("--http-logging", envvar = "Enable all HTTP logging").flag()
    val concurrency: Int by option(
        "--concurrency",
        "-C",
        envvar = "CONCURRENCY",
        help = "Translate chunks concurrently. (WARN: parallelism will cause terms to be ineffective)"
    ).int().default(1)
    val temperature by option(
        "--temperature", envvar = "OPENAI_TEMPERATURE", help = "Temperature for the model (0.0-2.0)"
    ).double()

    // prompts

    val targetLanguage by option(
        "--target-language",
        envvar = "TARGET_LANGUAGE",
        help = "Target language for translation (e.g. 简体中文, English, 日本語)"
    ).default(TranslationPrompts.targetLanguage)

    val literatureStyle by option(
        "--literature-style", help = "Custom literature style prompt for translation"
    ).default(
        TranslationPrompts.literatureStyle
    )

    val mapInfoFile by option(
        "--map-info", help = "Provide map info to help LLM better understand context"
    ).path()

    val extraPrompts by option(
        "--extra-prompts",
        help = "That will be appended to the end of all prompts; it'll DAMAGE AI Translate if FILLED OUT IMPROPERLY"
    )

    val mapInfo by lazy { mapInfoFile?.readJson<MapInfo>() ?: MapInfo.None }

    context(_: Raise<ChatCompletionCallError>)
    suspend fun creatCall() = context(env) {
        ChatCompletionCall(
            apiUrl = apiUrl,
            token = token,
            model = model,
            useStreamApi = useStreamApi,
            temperature = temperature,
            logLevel = if (enableHttpLogging) LogLevel.All else LogLevel.None,
        )
    }
}


class TermExtract : AICommand(
    name = "term-extract", help = "Extract term from text pool"
) {
    val input by option("--input", "-i", help = "The path to the JSON file oftext pool").path().required()
    val termCaches by option(
        "--term-caches", "-c", help = "The term file exported by the follow `--output`. Default: no terms"
    ).path()
    val output by option("--output", "-o", help = "The output path for the term table JSON").path().required()

    context(_: Raise<MCTError>)
    override suspend fun App() {
        logger.info { "Loading text from $input" }
        val texts = input.jsonFile<TranslationPool>()
        val termCaches = this@TermExtract.termCaches.jsonFile<TermTable>(emptyMap())
        var consumedTokenCount = 0
        NotifierHooks.onAiSign {
            if (it is AiSign.ConsumeToken) {
                consumedTokenCount += it.count
            }
        }

        val extractor = TermExtractor(
            call = creatCall(),
            defaultTerms = termCaches,
            tokenThreshold = tokenThreshold,
            prompts = TermExtractionPrompts(
                targetLanguage = targetLanguage,
                literatureStyle = literatureStyle,
                mapInfo = mapInfo,
                extraPrompts = extraPrompts
            ),
            concurrency = concurrency,
        )

        logger.info { "Starting extraction..." }
        val terms = extractor.extract(texts) { terms ->
            logger.info { "Salvage ${terms.size} terms" }
        }
        output.writeJson(terms)
        logger.info { "Extraction done, ${terms.size} terms, and consume $consumedTokenCount tokens." }
    }
}

class AITranslate : AICommand(
    name = "translate", help = "Translate via OpenAI api"
) {
    val input by option("--input", "-i", help = "The extraction JSON file to translate").path().required()
    val caches by option(
        "--cache-mapping", "-cm", help = "The cache mapping file exported by the follow `--mapping`. By default"
    ).path()
    val output by option("--output", "-o", help = "The output path for the replacements JSON").path().required()
    val mappingOutput by option("--output-mapping", "-om", help = "The output path for the mapping JSON").path()
        .required()
    val termOutput by option("--output-term", "-ot", help = "The output path for the term table JSON").path().required()
    val term by option("--term", help = "Path to an existing term table JSON file").path()

    val concurrentByKind by option("--concurrent-by-kind", "-K").flag()

    val handleGradient by option(
        "--handle-gradient", help = "Enable aggressive gradient text handling"
    ).flag()


    context(_: Raise<MCTError>)
    override suspend fun App() {
        logger.info { "Loading extractions from $input" }
        val extractionGroups = input.jsonFile<List<ExtractionGroup>>()
        val terms = term.jsonFile<TermTable>(emptyMap())
        val caches = caches.jsonFile<Map<String, String>>(emptyMap())
        var consumedTokenCount = 0L
        NotifierHooks.onAiSign {
            if (it is AiSign.ConsumeToken) {
                consumedTokenCount += it.count
            }
        }

        logger.info { "Loaded ${extractionGroups.size} groups, ${terms.size} existing terms" }

        val translator = Translator.Companion(
            call = creatCall(),
            customizedPrompts = TranslationPrompts(
                literatureStyle = literatureStyle,
                targetLanguage = targetLanguage,
                handleGradientAggressively = handleGradient,
                mapInfo = mapInfo,
                extraPrompts = extraPrompts
            ),
            defaultTerms = terms,
            tokenThreshold = tokenThreshold,
            concurrency = concurrency,
        )


        logger.info { "Starting translation..." }
        val mapping = translator.translate(extractionGroups, caches, concurrentByKind) { terms, salvaged ->
            mappingOutput.writeJson(caches + salvaged)
            termOutput.writeJson(terms)
        }
        val replacements = extractionGroups.replace(mapping)

        logger.info { "Translation done, ${replacements.size} replacement groups" }

        logger.info { "Writing mapping to $mappingOutput" }
        mappingOutput.writeJson(mapping)
        logger.info { "Writing replacements to $output" }
        output.writeJson(replacements)
        logger.info { "Writing ${translator.terms.size} terms to $termOutput" }
        termOutput.writeJson(translator.terms)
        logger.info { "Done and consume $consumedTokenCount tokens." }
    }
}