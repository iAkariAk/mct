package mct.gui.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mct.MCTPattern
import mct.extra.ai.translator.LLMTranslationPrompts
import mct.extra.ai.translator.MapInfo
import mct.extra.ai.translator.Translator
import mct.model.patch.PatchValidationFailureStrategy
import mct.model.patch.PathKind

/**
 * Where text is extracted from.
 *
 * [label] is kept short because it is rendered inside the equal-width mode selector; the
 * longer [description] is shown as supporting text under that selector.
 */
enum class RunMode(val key: String, val label: String, val description: String) {
    Region("region", "Region (.mca)", "读取区域文件，提取告示牌、方块实体等 NBT 文本。"),
    Datapack("datapack", "Datapack", "读取数据包，提取 MCFunction 命令与 JSON 文本。"),
    Cext("cext", "Cext", "按自定义规则文件中的路径正则提取文本。"),
    ;

    /**
     * The rule categories this extraction mode actually uses; unlisted categories are not
     * consulted.
     *
     * Returns a shared constant list: allocating a fresh list on every access would stop the
     * rule editor receiving it from ever skipping recomposition.
     */
    val patternSlots: List<MCTPatternSlot>
        get() = when (this) {
            Region -> RegionPatternSlots
            Datapack -> DatapackPatternSlots
            Cext -> MCTPatternSlot.entries
        }
}

private val RegionPatternSlots = listOf(
    MCTPatternSlot.Nbt,
    MCTPatternSlot.Command,
    MCTPatternSlot.CommandData,
    MCTPatternSlot.CommandRegex,
)

private val DatapackPatternSlots = listOf(
    MCTPatternSlot.McJson,
    MCTPatternSlot.Command,
    MCTPatternSlot.CommandData,
    MCTPatternSlot.CommandRegex,
)

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

/** Translation engine. For [Api], [ApiTranslateState.kind] selects the concrete service. */
@Serializable
enum class TranslationEngine(val label: String) {
    @SerialName("ai")
    Ai("AI 翻译"),

    @SerialName("api")
    Api("API 翻译"),
}

/** Concrete service behind [TranslationEngine.Api]. */
@Serializable
enum class TranslationApiKind(val label: String) {
    @SerialName("mtran_server")
    MTranServer("MTranServer"),
}

/** Configuration for the API translation engine. */
@Serializable
@Immutable
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
 * One category of rules inside [MCTPattern].
 *
 * [builtinToggle] / [filterToggle] mirror which switches the CLI supports:
 * - The built-in switch only matters once a custom rule file is set (off = custom rules
 *   only).
 * - With the filter switch off the whole category stops filtering, like the CLI's
 *   `--disable-filter-*` flags.
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

/** Configuration of one rule category: rule file plus the built-in and filter switches. */
@Immutable
data class MCTPatternEntry(
    val path: String = "",
    val useBuiltin: Boolean = true,
    val filtering: Boolean = true,
)

/**
 * UI state for [MCTPattern].
 *
 * Every construction goes through [mct.gui.services.composePattern]; the UI only edits the
 * entries held here.
 */
@Immutable
data class MCTPatternState(
    val entries: Map<MCTPatternSlot, MCTPatternEntry> = emptyMap(),
) {
    operator fun get(slot: MCTPatternSlot): MCTPatternEntry = entries[slot] ?: MCTPatternEntry()

    fun with(slot: MCTPatternSlot, entry: MCTPatternEntry): MCTPatternState =
        copy(entries = entries + (slot to entry))
}

@Immutable
data class ExtractState(
    val input: String = "",
    val output: String = "extractions.json",
    val mode: RunMode = RunMode.Region,
    val patterns: MCTPatternState = MCTPatternState(),
)

@Immutable
data class PatchCreateState(
    val input: String = "",
    val mapping: String = "mappings.json",
    val output: String = "patch.json",
    val kind: PatchKind = PatchKind.Immediate,
    val format: PatchFormat = PatchFormat.Json,
    val validation: Boolean = true,
    val patterns: MCTPatternState = MCTPatternState(),
)

@Immutable
data class PatchApplyState(
    val input: String = "",
    val patch: String = "",
    val format: PatchFormat = PatchFormat.Json,
    val strategy: PatchStrategy = PatchStrategy.Warning,
)

@Immutable
data class PatchState(
    val section: PatchSection = PatchSection.Create,
    val create: PatchCreateState = PatchCreateState(),
    val apply: PatchApplyState = PatchApplyState(),
)

@Immutable
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

@Immutable
data class BackfillState(
    val input: String = "",
    val replacements: String = "replacements.json",
    val mode: RunMode = RunMode.Region,
)

@Immutable
data class TermExtractState(
    val input: String = "extractions.json",
    val output: String = "terms.json",
    val existingTermPath: String = "",
    val targetLanguage: String = LLMTranslationPrompts.targetLanguage,
    val literatureStyle: String = LLMTranslationPrompts.literatureStyle,
    val mapInfo: MapInfo = LLMTranslationPrompts.mapInfo,
    val extraPrompts: String = LLMTranslationPrompts.extraPrompts.orEmpty(),
)

@Immutable
data class ProjectWorkflowState(
    val directory: String = "",
    val name: String = "",
    val source: String = "",
)

@Immutable
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
