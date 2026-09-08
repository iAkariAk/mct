package mct.cli.cmd.project

import com.akuleshov7.ktoml.annotations.TomlComments
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mct.extra.ai.ChatCompletionCall
import mct.extra.ai.translator.*
import mct.model.patch.PathKind

@Serializable
@SerialName("project")
data class ProjectConfig(
    @TomlComments("Project name")
    val name: String,

    @TomlComments("Project version")
    val version: String? = null,

    @TomlComments("Project description")
    val description: String? = null,

    @TomlComments("Path to the translation mapping JSON file (source -> target)")
    val mappings: String = "mappings.json",

    @TomlComments("Path to the MTLX file (default: null -> not to use MTLX")
    val mtlx: String? = null,

    @TomlComments("Path to the term table JSON file")
    val terms: String = "terms.json",

    @TomlComments("Use pretty-printed JSON output")
    @SerialName("pretty_json")
    val prettyJson: Boolean = true,

    @TomlComments("Extraction pattern configuration")
    val patterns: PatternsConfig = PatternsConfig.Empty,

    @TomlComments("Provide map info to help LLM better understand context")
    @SerialName("map_info")
    val mapInfo: MapInfo = MapInfo.None,

    @TomlComments("AI translation configuration")
    val ai: AIConfig = AIConfig.Default,

    @TomlComments("Api translation configuration")
    val translation: TranslationConfig = TranslationConfig.AI,

    @TomlComments("MCT Patch configuration")
    val patch: PatchConfig = PatchConfig.Default,
)

@Serializable
@SerialName("patterns")
data class PatternsConfig(
    @TomlComments("Paths to region data-pointer pattern JSON files (extract texts from block entities, signs, etc.)")
    val nbt: Set<String> = emptySet(),

    @TomlComments("Paths to mcjson data-pointer pattern JSON files")
    val mcjson: Set<String> = emptySet(),

    @TomlComments("Paths to command extract pattern JSON files")
    val command: Set<String> = emptySet(),

    @SerialName("command_data")
    @TomlComments("Paths to command SNBT data-pointer pattern JSON files (extract data from command arguments)")
    val commandData: Set<String> = emptySet(),

    @SerialName("command_component")
    @TomlComments("Paths to command component pattern JSON files (extract data from command arguments which has component)")
    val commandComponent: Set<String> = emptySet(),

    @SerialName("command_regex")
    @TomlComments("Paths to command regex pattern JSON files")
    val commandRegex: Set<String> = emptySet(),

    @SerialName("cext")
    @TomlComments("Paths to cext pattern JSON files")
    val cext: List<String> = emptyList(),
) {
    companion object {
        val Empty = PatternsConfig()
    }
}

@Serializable
sealed class TranslationConfig {
    @Serializable
    @SerialName("ai")
    data object AI : TranslationConfig() {
        fun createTranslator(call: ChatCompletionCall, config: ProjectConfig, existingTerms: TermTable): LLMTranslator {
            val ai = config.ai
            return LLMTranslator(
                call = call,
                customizedPrompts = LLMTranslationPrompts(
                    literatureStyle = ai.literatureStyle,
                    targetLanguage = ai.targetLanguage,
                    handleGradientAggressively = ai.handleGradientAggressively,
                    mapInfo = config.mapInfo,
                    extraPrompts = ai.extraPrompts,
                ), defaultTerms = existingTerms, tokenThreshold = ai.tokenThreshold, concurrency = ai.concurrency
            )
        }

    }

    @Serializable
    @SerialName("api")
    sealed class Api : TranslationConfig() {
        @SerialName("max_retry")
        abstract val maxRetry: Int

        @TomlComments("Source language code, auto when empty")
        abstract val from: String?

        @TomlComments("Target language code")
        abstract val to: String

        abstract fun display(): String

        override fun toString(): String = "${display()}{maxRetry=$maxRetry, from=$from, to=$to}"

        @Serializable
        @SerialName("MTranServer")
        data class MTranServer(
            @SerialName("api_url")
            val apiUrl: String,
            val token: String? = null,
            override val from: String? = null,
            override val to: String,
            override val maxRetry: Int = Translator.MAX_RETRY_COUNT
        ) : Api() {
            override fun createApi(): TranslationApi =
                TranslationApis.MTranServerTranslation(apiUrl, token, maxRetry, from, to)

            override fun display(): String = "MTranServer($apiUrl)"

        }

        abstract fun createApi(): TranslationApi

        fun createTranslator(): Translator = ApiTranslator(createApi())
    }
}

@Serializable
@SerialName("ai")
data class AIConfig(
    @SerialName("api_url")
    @TomlComments("OpenAI-compatible API base URL (e.g. https://api.openai.com/ or https://api.deepseek.com/)")
    val apiUrl: String = "https://api.openai.com/",

    @TomlComments("API access token")
    val token: String = "Token / API Key",

    @TomlComments("Model name (e.g. gpt-4o, deepseek-v4-pro, gemini-2.0-flash)")
    val model: String = "gpt-4o",

    @SerialName("use_stream_api")
    @TomlComments("Use streaming API (can resolve empty response issues on some providers; default: true)")
    val useStreamApi: Boolean = true,

    @SerialName("token_threshold")
    @TomlComments("Max tokens per translation request")
    val tokenThreshold: Int = 2048,

    @SerialName("literature_style")
    @TomlComments("Custom literature-style prompt for translation")
    val literatureStyle: String = LLMTranslationPrompts.literatureStyle,

    @TomlComments("Target language (e.g. 简体中文, English, 日本語; default: ${LLMTranslationPrompts.targetLanguage})") @SerialName(
        "target_language"
    )
    val targetLanguage: String = LLMTranslationPrompts.targetLanguage,

    @TomlComments("That will be appended to the end of all prompts; it'll DAMAGE AI Translate if FILLED OUT IMPROPERLY") @SerialName(
        "extra_prompts"
    )
    val extraPrompts: String? = LLMTranslationPrompts.extraPrompts,

    @TomlComments("Temperature for the AI model (0.0-2.0, null = use model default, i.e. 1.0)")
    val temperature: Double? = 1.0,

    @TomlComments("Enable aggressive gradient text handling (default: ${LLMTranslationPrompts.handleGradientAggressively})") @SerialName(
        "handle_gradient"
    )
    val handleGradientAggressively: Boolean = LLMTranslationPrompts.handleGradientAggressively,

    @TomlComments("Enable http logging for debug (default: false)") @SerialName("http_logging")
    val enableHttpLogging: Boolean = false,

    @TomlComments("Enable LLM thinking output (default: false)") @SerialName("thinking_output")
    val enableThinkingOutput: Boolean = false,

    @TomlComments("Translate chunks concurrently. (WARN: parallelism will cause terms to be ineffective; default: 1)")
    val concurrency: Int = 1,

    @TomlComments("Translate different kinds of extraction concurrently. (WARN: parallelism will cause terms to be ineffective; default: false)") @SerialName(
        "concurrent_by_kind"
    )
    val concurrentByKind: Boolean = false,
) {
    companion object {
        val Default = AIConfig()
    }
}

@Serializable
@SerialName("patch")
data class PatchConfig(
    @TomlComments("Name of the created patch (default follow `Project name`)")
    val name: String? = null,
    @TomlComments("Kind of the created patch; `immediate` will evaluate the replacement groups immediately, `deferred` will evaluate that when apply the patch (default: immediate)")
    val kind: PathKind = Immediate,
) {
    companion object {
        val Default = PatchConfig()
    }
}