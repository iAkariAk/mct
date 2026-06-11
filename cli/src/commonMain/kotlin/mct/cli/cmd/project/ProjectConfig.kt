package mct.cli.cmd.project

import com.akuleshov7.ktoml.annotations.TomlComments
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    val regions: Set<String> = emptySet(),

    @TomlComments("Paths to mcfunction command-extract pattern JSON files")
    val mcfunction: Set<String> = emptySet(),

    @SerialName("mcfunction_data")
    @TomlComments("Paths to mcfunction SNBT data-pointer pattern JSON files (extract data from command arguments)")
    val mcfunctionData: Set<String> = emptySet(),

    @TomlComments("Paths to mcjson data-pointer pattern JSON files")
    val mcjson: Set<String> = emptySet(),
) {
    companion object {
        val Empty = PatternsConfig()
    }
}

@Serializable
@SerialName("ai")
data class AIConfig(
    @SerialName("api_url")
    @TomlComments("OpenAI-compatible API base URL (e.g. https://api.openai.com/v1)")
    val apiUrl: String? = null,

    @TomlComments("API access token")
    val token: String = "Token / API Key",

    @TomlComments("Model name (e.g. gpt-4o, deepseek-v4-pro, gemini-2.0-flash)")
    val model: String = "gpt-4o",

    @TomlComments("Use streaming API (can resolve empty response issues on some providers)")
    @SerialName("use_stream_api")
    val useStreamApi: Boolean = true,

    @TomlComments("Max tokens per translation request")
    @SerialName("token_threshold")
    val tokenThreshold: Int = 2048,

    @TomlComments("Custom literature-style prompt for translation")
    @SerialName("literature_style")
    val literatureStyle: String? = null,

    @TomlComments("Target language (e.g. Simplified Chinese, English, 日本語)")
    @SerialName("target_language")
    val targetLanguage: String? = null,

    @TomlComments("Temperature for the AI model (0.0-2.0, null = use model default)")
    val temperature: Double? = null,

    @TomlComments("Enable aggressive gradient text handling (default: true)")
    @SerialName("handle_gradient")
    val handleGradientAggressively: Boolean? = null,
) {
    companion object {
        val Default = AIConfig()
    }
}