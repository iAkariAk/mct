package mct.gui.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mct.MCTPattern
import mct.extra.ai.translator.LLMTranslationPrompts
import mct.extra.ai.translator.MapInfo
import mct.extra.ai.translator.Translator
import mct.model.patch.PatchValidationFailureStrategy
import mct.model.patch.PathKind

enum class RunMode(val key: String, val label: String) {
    Region("region", "Region (.mca 区域文件)"),
    Datapack("datapack", "Datapack (数据包)"),
    Cext("cext", "Cext (自定义提取)"),
    ;

    /** 该提取模式真正会使用的规则类别，未列出的类别不会参与本次提取。 */
    val patternSlots: List<MCTPatternSlot>
        get() = when (this) {
            Region -> listOf(MCTPatternSlot.Nbt, MCTPatternSlot.Command, MCTPatternSlot.CommandData, MCTPatternSlot.CommandRegex)
            Datapack -> listOf(MCTPatternSlot.McJson, MCTPatternSlot.Command, MCTPatternSlot.CommandData, MCTPatternSlot.CommandRegex)
            Cext -> MCTPatternSlot.entries
        }
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

/** 翻译引擎；选择 [Api] 时具体走哪套接口由 [ApiTranslateState.kind] 决定。 */
@Serializable
enum class TranslationEngine(val label: String) {
    @SerialName("ai")
    Ai("AI 翻译"),

    @SerialName("api")
    Api("API 翻译"),
}

/** [TranslationEngine.Api] 的具体实现。 */
@Serializable
enum class TranslationApiKind(val label: String) {
    @SerialName("mtran_server")
    MTranServer("MTranServer"),
}

/** API 翻译引擎的配置。 */
@Serializable
data class ApiTranslateState(
    val kind: TranslationApiKind = TranslationApiKind.MTranServer,
    val url: String = "http://127.0.0.1:8989/",
    val token: String = "",
    val sourceLanguage: String = "",
    val targetLanguage: String = "zh_cn",
    val maxRetry: String = Translator.MAX_RETRY_COUNT.toString(),
)

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

/**
 * [MCTPattern] 中的一类规则。
 *
 * [builtinToggle] / [filterToggle] 对应 CLI 规则开关的可用性：
 * - 内置规则开关仅在填写了自定义规则文件后有意义（关闭则只用自定义规则）；
 * - 过滤开关关闭时该类规则整体不参与过滤（等价于 CLI 的 `--disable-filter-*`）。
 */
enum class MCTPatternSlot(
    val label: String,
    val description: String,
    val builtinToggle: Boolean = false,
    val filterToggle: Boolean = false,
) {
    Nbt("Region NBT 规则", "告示牌、方块实体等 Region NBT 文本", builtinToggle = true, filterToggle = true),
    McJson("MCJson 规则", "数据包内 JSON 文本", builtinToggle = true, filterToggle = true),
    Command("MCFunction 命令规则", "命令文本提取规则，如 say、title", builtinToggle = true),
    CommandData("Command Data 规则", "命令参数中的 SNBT / 文本组件数据", builtinToggle = true, filterToggle = true),
    CommandComponent("Command Component 规则", "命令参数中带 component 的数据", builtinToggle = true),
    CommandRegex("Command 正则规则", "补充的正则提取规则"),
    Cext("Cext 自定义规则", "按文件路径正则匹配的自定义提取规则"),
}

/** 单个规则类别的配置：自定义规则文件 + 内置规则/过滤开关。 */
data class MCTPatternEntry(
    val path: String = "",
    val useBuiltin: Boolean = true,
    val filtering: Boolean = true,
)

/**
 * [MCTPattern] 的界面状态。
 *
 * 所有构造都收敛到 [mct.gui.services.composePattern]，界面只负责编辑这里的每一项。
 */
data class MCTPatternState(
    val entries: Map<MCTPatternSlot, MCTPatternEntry> = emptyMap(),
) {
    operator fun get(slot: MCTPatternSlot): MCTPatternEntry = entries[slot] ?: MCTPatternEntry()

    fun with(slot: MCTPatternSlot, entry: MCTPatternEntry): MCTPatternState =
        copy(entries = entries + (slot to entry))
}

data class ExtractState(
    val input: String = "",
    val output: String = "extractions.json",
    val mode: RunMode = RunMode.Region,
    val patterns: MCTPatternState = MCTPatternState(),
)

data class PatchCreateState(
    val input: String = "",
    val mapping: String = "mappings.json",
    val output: String = "patch.json",
    val kind: PatchKind = PatchKind.Immediate,
    val format: PatchFormat = PatchFormat.Json,
    val validation: Boolean = true,
    val patterns: MCTPatternState = MCTPatternState(),
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
    val engine: TranslationEngine = TranslationEngine.Ai,
    val api: ApiTranslateState = ApiTranslateState(),
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
    val commandPatterns: MCTPatternState = MCTPatternState(),
    val commandResult: String = "",
    val officialSourceLanguage: String = "",
    val officialTargetLanguage: String = "",
    val officialMinecraftVersion: String = "latest",
    val officialOutput: String = "",
    val officialConcurrency: String = "20",
)
