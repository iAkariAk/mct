package mct.gui.model

import mct.extra.ai.translator.LLMTranslationPrompts
import mct.extra.ai.translator.MapInfo
import mct.extra.ai.translator.Translator
import mct.model.patch.PatchValidationFailureStrategy
import mct.model.patch.PathKind

enum class RunMode(val key: String, val label: String) {
    Region("region", "Region (.mca 区域文件)"),
    Datapack("datapack", "Datapack (数据包)"),
    Cext("cext", "Cext (自定义提取)"),
}

enum class PointerKind(val key: String, val label: String) {
    Region("region", "Region"),
    McJson("mcjson", "MCJson"),
}

enum class ToolboxOperation(val title: String, val actionLabel: String) {
    PointerTest("DataPointer 匹配测试", "测试匹配"),
    ExportSnbt("导出 Region SNBT", "导出 SNBT"),
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

enum class TranslationEngineKind(val label: String) {
    Ai("AI 翻译"),
    Api("API 翻译 (MTranServer)"),
}

enum class MtlxSource(val label: String) {
    Pool("文本池"),
    Mapping("翻译映射"),
}

enum class PatchSection(val label: String) {
    Create("创建补丁"),
    Apply("应用补丁"),
}

enum class PatchKind(val label: String, val value: PathKind) {
    Immediate("立即求值", PathKind.Immediate),
    Deferred("延迟求值", PathKind.Deferred),
}

enum class PatchFormat(val label: String, val extension: String) {
    Json("JSON", "json"),
    Cbor("CBOR", "mctp"),
}

enum class PatchStrategy(val label: String, val value: PatchValidationFailureStrategy) {
    Ignore("忽略校验", PatchValidationFailureStrategy.Ignore),
    Warning("警告并继续", PatchValidationFailureStrategy.Warning),
    Failure("校验失败则中止", PatchValidationFailureStrategy.Failure),
}

/** 提取与补丁共用的规则文件路径；留空表示使用内置规则。 */
data class PatternState(
    val regionPatternPath: String = "",
    val commandPatternPath: String = "",
    val commandDataPatternPath: String = "",
    val mcjPatternPath: String = "",
    val commandRegexPatternPath: String = "",
    val cextPatternPath: String = "",
)

data class ExtractState(
    val input: String = "",
    val output: String = "extractions.json",
    val mode: RunMode = RunMode.Region,
    val disableFilter: Boolean = false,
    val patterns: PatternState = PatternState(),
)

data class PatchCreateState(
    val input: String = "",
    val mapping: String = "mappings.json",
    val output: String = "patch.json",
    val kind: PatchKind = PatchKind.Immediate,
    val format: PatchFormat = PatchFormat.Json,
    val validation: Boolean = true,
    val patterns: PatternState = PatternState(),
)

data class PatchApplyState(
    val input: String = "",
    val patch: String = "",
    val format: PatchFormat = PatchFormat.Json,
    val strategy: PatchStrategy = PatchStrategy.Warning,
)

data class PatchState(
    val section: PatchSection = PatchSection.Create,
    val create: PatchCreateState = PatchCreateState(),
    val apply: PatchApplyState = PatchApplyState(),
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
    val literatureStyle: String = LLMTranslationPrompts.literatureStyle,
    val targetLanguage: String = LLMTranslationPrompts.targetLanguage,
    val isOptimizing: Boolean = false,
    val handleGradientAggressively: Boolean = LLMTranslationPrompts.handleGradientAggressively,
    val mapInfo: MapInfo = LLMTranslationPrompts.mapInfo,
    val extraPrompts: String = LLMTranslationPrompts.extraPrompts.orEmpty(),
    val engine: TranslationEngineKind = TranslationEngineKind.Ai,
    val apiTranslateUrl: String = "http://127.0.0.1:8989/",
    val apiTranslateToken: String = "",
    val apiSourceLanguage: String = "",
    val apiTargetLanguage: String = "zh_cn",
    val apiMaxRetry: String = Translator.MAX_RETRY_COUNT.toString(),
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
    val targetLanguage: String = LLMTranslationPrompts.targetLanguage,
    val literatureStyle: String = LLMTranslationPrompts.literatureStyle,
    val mapInfo: MapInfo = LLMTranslationPrompts.mapInfo,
    val extraPrompts: String = LLMTranslationPrompts.extraPrompts.orEmpty(),
)

data class ProjectWorkflowState(
    val directory: String = "",
    val name: String = "",
    val source: String = "",
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
    val mtlxSource: MtlxSource = MtlxSource.Pool,
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
