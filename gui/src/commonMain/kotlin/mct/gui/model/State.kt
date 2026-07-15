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

enum class ToolboxOperation(val title: String, val actionLabel: String) {
    FlattenPool("展开文本池", "展开"),
    UnflattenPool("压缩文本池", "压缩"),
    GenerateMtlx("生成 MTLX 模板", "生成"),
    TranslateMtlx("翻译 MTLX", "翻译"),
    ReplaceAll("批量替换", "生成替换"),
    ExportSchema("导出 Schema", "导出"),
    CommandTest("Command Pattern 测试", "测试"),
    DownloadOfficialLanguage("下载官方语言", "下载"),
    CombineOfficialLanguage("合并官方语言", "合并"),
}

enum class SchemaKind(val key: String, val label: String) {
    Command("command", "Command Pattern"),
    DataPointer("data_pointer", "DataPointer Pattern"),
    CommandRegex("command_regex", "Command Regex Pattern"),
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

data class ProjectWorkflowState(
    val directory: String = "",
    val name: String = "",
    val source: String = "",
    val target: String = "",
)

data class ToolboxState(
    val pointerKind: PointerKind = PointerKind.Region,
    val pointerPatternPath: String = "",
    val noBuiltin: Boolean = false,
    val pointerInput: String = "",
    val pointerResult: String? = null,
    val exportInput: String = "",
    val exportOutput: String = "",
    val activeOperation: ToolboxOperation? = null,
    val poolInput: String = "extractions.json",
    val poolOutput: String = "text-pool.json",
    val poolKind: RunMode = RunMode.Region,
    val poolSimply: Boolean = false,
    val mappingInput: String = "mappings.json",
    val mtlxInput: String = "translation.mtlx",
    val replacement: String = "\"MCT\"",
    val schemaKind: SchemaKind = SchemaKind.Command,
    val commandInput: String = "",
    val commandPatternPath: String = "",
    val commandDataPatternPath: String = "",
    val commandNoBuiltin: Boolean = false,
    val commandResult: String = "",
    val officialSourceLanguage: String = "",
    val officialTargetLanguage: String = "",
    val officialMinecraftVersion: String = "latest",
    val officialOutput: String = "",
    val officialConcurrency: String = "20",
)
