package mct.gui.model

import mct.extra.ai.translator.MapInfo
import mct.extra.ai.translator.TranslationPrompts

enum class RunMode(val key: String, val label: String) {
    Region("region", "Region (.mca 区域文件)"),
    Datapack("datapack", "Datapack (数据包)")
}

enum class PointerKind(val key: String, val label: String) {
    Region("region", "Region"),
    McJson("mcjson", "MCJson"),
}

data class ExtractState(
    val input: String = "",
    val output: String = "extractions.json",
    val mode: RunMode = RunMode.Region,
    val disableFilter: Boolean = false,
    val regionPatternPath: String = "",
    val commandPatternPath: String = "",
    val commandDataPatternPath: String = "",
    val mcjPatternPath: String = "",
    val commandRegexPatternPath: String = "",
)

data class TranslateState(
    val input: String = "extractions.json",
    val output: String = "replacements.json",
    val mappingOutput: String = "mappings.json",
    val termOutput: String = "terms.json",
    val cachesPath: String = "",
    val apiUrl: String = "",
    val apiToken: String = "",
    val model: String = "gpt-4o",
    val availableModels: List<String> = emptyList(),
    val isModelsLoading: Boolean = false,
    val existingTermPath: String = "",
    val literatureStyle: String = TranslationPrompts.literatureStyle,
    val targetLanguage: String = TranslationPrompts.targetLanguage,
    val isOptimizing: Boolean = false,
    val handleGradientAggressively: Boolean = TranslationPrompts.handleGradientAggressively,
    val mapInfo: MapInfo = TranslationPrompts.mapInfo,
    val extraPrompts: String = TranslationPrompts.extraPrompts.orEmpty(),
)

data class BackfillState(
    val input: String = "",
    val replacements: String = "replacements.json",
    val mode: RunMode = RunMode.Region,
)

data class TermExtractState(
    val input: String = "extractions.json",
    val output: String = "terms.json",
    val existingTermPath: String = "",
    val targetLanguage: String = TranslationPrompts.targetLanguage,
    val literatureStyle: String = TranslationPrompts.literatureStyle,
    val mapInfo: MapInfo = TranslationPrompts.mapInfo,
    val extraPrompts: String = TranslationPrompts.extraPrompts.orEmpty(),
)

data class ToolboxState(
    val pointerKind: PointerKind = PointerKind.Region,
    val pointerPatternPath: String = "",
    val noBuiltin: Boolean = false,
    val pointerInput: String = "",
    val pointerResult: String? = null,
    val exportInput: String = "",
    val exportOutput: String = "",
)
