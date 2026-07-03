package mct.cli.cmd.project

import com.akuleshov7.ktoml.annotations.TomlComments
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mct.extra.ai.translator.CustomizedPrompts

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

    @TomlComments("AI translation configuration")
    val ai: AIConfig = AIConfig.Default,
)

@Serializable
@SerialName("patterns")
data class PatternsConfig(
    @TomlComments("Paths to region data-pointer pattern JSON files (extract texts from block entities, signs, etc.)")
    val nbt: Set<String> = emptySet(),

    @TomlComments("Paths to command extract pattern JSON files")
    val command: Set<String> = emptySet(),

    @SerialName("command_data")
    @TomlComments("Paths to command SNBT data-pointer pattern JSON files (extract data from command arguments)")
    val commandData: Set<String> = emptySet(),

    @TomlComments("Paths to mcjson data-pointer pattern JSON files")
    val mcjson: Set<String> = emptySet(),

    @SerialName("command_regex")
    @TomlComments("Paths to command regex pattern JSON files")
    val commandRegex: Set<String> = emptySet(),
) {
    companion object {
        val Empty = PatternsConfig()
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

    @TomlComments("Use streaming API (can resolve empty response issues on some providers; default: true)")
    @SerialName("use_stream_api")
    val useStreamApi: Boolean = true,

    @TomlComments("Max tokens per translation request")
    @SerialName("token_threshold")
    val tokenThreshold: Int = 2048,

    @TomlComments("Custom literature-style prompt for translation")
    @SerialName("literature_style")
    val literatureStyle: String = CustomizedPrompts.literatureStyle,

    @TomlComments("Target language (e.g. 简体中文, English, 日本語; default: ${CustomizedPrompts.targetLanguage})")
    @SerialName("target_language")
    val targetLanguage: String = CustomizedPrompts.targetLanguage,

    @TomlComments("Temperature for the AI model (0.0-2.0, null = use model default, i.e. 1.0)")
    val temperature: Double? = 1.0,

    @TomlComments("Enable aggressive gradient text handling (default: ${CustomizedPrompts.handleGradientAggressively})")
    @SerialName("handle_gradient")
    val handleGradientAggressively: Boolean = CustomizedPrompts.handleGradientAggressively,

    @TomlComments("Enable http logging for debug (default: false)")
    @SerialName("http_logging")
    val enableHttpLogging: Boolean = false,

    @TomlComments("Enable LLM thinking output (default: false)")
    @SerialName("thinking_output")
    val enableThinkingOutput: Boolean = false,

    @TomlComments("Translate chunks concurrently. (WARN: parallelism will cause terms to be ineffective; default: 1)")
    val concurrency: Int = 1,

    @TomlComments("Translate different kinds of extraction concurrently. (WARN: parallelism will cause terms to be ineffective; default: false)")
    @SerialName("concurrent_by_kind")
    val concurrentByKind: Boolean = false,
    ) {
    companion object {
        val Default = AIConfig()
    }
}