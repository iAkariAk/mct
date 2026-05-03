package mct.gui

import arrow.core.raise.Raise
import arrow.core.raise.either
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mct.*
import mct.dp.backfillDatapack
import mct.dp.compile
import mct.dp.extractFromDatapackRaw
import mct.dp.mcfunction.ExtractPattern
import mct.extra.ai.ChatCompletionCallError
import mct.extra.ai.translator.CustomizedPrompts
import mct.extra.ai.translator.OpenAITranslator
import mct.extra.ai.translator.TermTable
import mct.extra.ai.translator.translate
import mct.kit.replace
import mct.pointer.CustomizedDataPointerPattern
import mct.region.BuiltinRegionPatterns
import mct.region.backfillRegion
import mct.region.extractFromRegion
import mct.serializer.MCTJson
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import mct.dp.mcfunction.BuiltinMCFPatterns as MCFBuiltinPatterns
import mct.dp.mcjson.BuiltinMCJPatterns as MCJBuiltinPatterns

// ── API 设置持久化 ──────────────────────────────────────────────

private val settingsJson = Json { ignoreUnknownKeys = true; encodeDefaults = false }
private val settingsPath: Path =
    "${System.getProperty("user.home")}/.mct/api-settings.json".toPath()

/** Full path string of the API settings file, for display in UI log messages. */
val settingsPathString: String get() = settingsPath.toString()

@Serializable
data class ApiSettings(
    val apiUrl: String = "",
    val model: String = "gpt-4o",
    val apiToken: String = "",
)

// ── 统一日志器 ──────────────────────────────────────────────────

/**
 * A [Logger] implementation for the GUI layer.
 * - Non-Sign messages (Info/Debug/Error/Warning) are forwarded to [onLog] with level prefix.
 * - Sign messages are silently dropped (they are intercepted upstream by `onSign<>` wrappers).
 */
class GuiLogger(
    private val onLog: (LogEntry) -> Unit
) : Logger(LoggerLevel.Verbose) {
    override fun log(level: LoggerLevel, message: String) {
        println(message)
        if (level == LoggerLevel.Sign) return
        onLog(LogEntry(level, message))
    }
}

// ── 设置读写 ──────────────────────────────────────────────────

fun loadSettings(): ApiSettings {
    return try {
        if (FileSystem.SYSTEM.exists(settingsPath)) {
            val text = FileSystem.SYSTEM.read(settingsPath) { readUtf8() }
            settingsJson.decodeFromString(text)
        } else ApiSettings()
    } catch (e: Exception) {
        println("[MCT] 加载API设置失败: ${e.message}")
        ApiSettings()
    }
}

fun saveSettings(apiUrl: String, model: String, apiToken: String): Boolean {
    return try {
        FileSystem.SYSTEM.createDirectories(settingsPath.parent!!)
        FileSystem.SYSTEM.write(settingsPath) {
            writeUtf8(settingsJson.encodeToString(ApiSettings(apiUrl, model, apiToken)))
        }
        true
    } catch (e: Exception) {
        println("[MCT] 保存API设置失败: ${e.message}")
        false
    }
}

// ── 后台任务 ─────────────────────────────────────────────────

/**
 * Run extraction in the background.
 *
 * All I/O uses [env.fs] (Okio). All status messages go through [env.logger].
 * Progress/signals are emitted via `env.logger.sign<>` and handled upstream by `onSign<>`.
 */
context(env: Env)
suspend fun runExtraction(
    input: String,
    output: String,
    mode: String,
    disableFilter: Boolean,
    regionPatternPath: String = "",
    mcfPatternPath: String = "",
    mcjPatternPath: String = "",
) {
    withContext(Dispatchers.IO) {
        env.logger.info { "正在打开: $input" }
        val inputPath = input.toPath()

        @Suppress("UNCHECKED_CAST")
        val result = either<MCTError, List<ExtractionGroup>> {
            val workspace = MCTWorkspace(inputPath, env)
            when (mode) {
                "region" -> {
                    val patterns = if (disableFilter) {
                        null
                    } else {
                        val userPatterns = regionPatternPath.takeIf { it.isNotBlank() }
                            ?.let { p ->
                                env.fs.read(p.toPath()) { readUtf8() }
                                    .let { MCTJson.decodeFromString<List<CustomizedDataPointerPattern>>(it) }
                                    .map { it.compile() }
                            }
                        if (userPatterns != null) BuiltinRegionPatterns.toList() + userPatterns
                        else BuiltinRegionPatterns.toList()
                    }
                    workspace.extractFromRegion(patterns = patterns).toList() as List<ExtractionGroup>
                }

                "datapack" -> {
                    val mcfPatterns = mcfPatternPath.takeIf { it.isNotBlank() }
                        ?.let { p ->
                            env.fs.read(p.toPath()) { readUtf8() }
                                .let { MCTJson.decodeFromString<List<ExtractPattern>>(it) }
                                .compile()
                        } ?: MCFBuiltinPatterns

                    val mcjPatterns: List<mct.pointer.DataPointerPattern>? =
                        if (disableFilter) {
                            null
                        } else {
                            val userPatterns = mcjPatternPath.takeIf { it.isNotBlank() }
                                ?.let { p ->
                                    env.fs.read(p.toPath()) { readUtf8() }
                                        .let { MCTJson.decodeFromString<List<CustomizedDataPointerPattern>>(it) }
                                        .map { it.compile() }
                                }
                            if (userPatterns != null) MCJBuiltinPatterns + userPatterns
                            else MCJBuiltinPatterns
                        }
                    workspace.extractFromDatapackRaw(mcfPatterns, mcjPatterns).toList() as List<ExtractionGroup>
                }

                else -> error("未知模式: $mode")
            }
        }

        result.fold(
            ifLeft = { env.logger.error { it.message } },
            ifRight = { groups ->
                val total = groups.sumOf { it.extractions.size }
                env.logger.info { "提取了 ${groups.size} 个分组, 共 $total 条文本" }
                val json = MCTJson.encodeToString(groups)
                env.fs.write(output.toPath()) { writeUtf8(json) }
                env.logger.info { "已写入: $output" }
                env.logger.info { "完成。" }
            }
        )
    }
}

/**
 * Run AI translation in the background.
 *
 * All I/O uses [env.fs] (Okio). Status messages use [env.logger].
 * TranslateSign signals (progress) are emitted via `env.logger.sign<>` and
 * handled by the `onSign<>` wrapper created at App level.
 */
context(env: Env, _: Raise<ChatCompletionCallError>)
suspend fun runTranslation(
    clientManager: ClientManager,
    input: String,
    output: String,
    mappingOutput: String,
    termOutput: String,
    apiUrl: String?,
    token: String,
    model: String,
    termPath: String?,
    literatureStyle: String = CustomizedPrompts.Defaults.literatureStyle,
    onFailure: ((ChatCompletionCallError) -> Unit)? = null
) {
    env.logger.info { "正在加载提取结果: $input" }

    val (extractionGroups, existingTerms) = withContext(Dispatchers.IO) {
        val json = env.fs.read(input.toPath()) { readUtf8() }
        val groups = MCTJson.decodeFromString<List<ExtractionGroup>>(json)
        env.logger.info { "已加载 ${groups.size} 个提取分组" }

        val terms: TermTable = if (termPath != null && env.fs.exists(termPath.toPath())) {
            val termJson = env.fs.read(termPath.toPath()) { readUtf8() }
            MCTJson.decodeFromString<TermTable>(termJson).also {
                env.logger.info { "已加载 ${it.size} 个已有术语" }
            }
        } else emptySet()

        groups to terms
    }

    val call = clientManager.chatCompletionCall
    if (call == null) {
        onFailure?.invoke(ChatCompletionCallError.UnvalidatedApi("没有 API 连接，请先在设置中配置"))
        return
    }

    val translator = OpenAITranslator(
        call = call,
        defaultTerms = existingTerms,
        customizedPrompts = CustomizedPrompts(literatureStyle = literatureStyle)
    )

    withContext(Dispatchers.IO) {
        try {
            val mapping = translator.translate(extractionGroups)
            val replacements = extractionGroups.replace(mapping)

            env.fs.write(output.toPath()) { writeUtf8(MCTJson.encodeToString(replacements)) }
            env.fs.write(mappingOutput.toPath()) { writeUtf8(MCTJson.encodeToString(mapping)) }
            env.fs.write(termOutput.toPath()) { writeUtf8(MCTJson.encodeToString(translator.terms)) }

            env.logger.info { "新发现 ${translator.terms.size - existingTerms.size} 个术语" }
            env.logger.info { "替换文件已写入: $output" }
            env.logger.info { "映射文件已写入: $mappingOutput" }
            env.logger.info { "术语表已写入: $termOutput" }
            env.logger.info { "完成。" }
            saveSettings(apiUrl ?: "", model, token)
        } catch (e: Exception) {
            env.logger.error { e.stackTraceToString() }
        }
    }
}

/**
 * Run backfill in the background.
 *
 * All I/O uses [env.fs] (Okio). All status messages go through [env.logger].
 */
suspend fun runBackfill(
    env: Env,
    input: String,
    replacementsFile: String,
    mode: String,
) {
    withContext(Dispatchers.IO) {
        env.logger.info { "正在打开存档: $input" }
        env.logger.info { "正在加载替换文件: $replacementsFile" }

        val json = env.fs.read(replacementsFile.toPath()) { readUtf8() }
        val all = MCTJson.decodeFromString<List<ReplacementGroup>>(json)
        env.logger.info { "已加载 ${all.size} 个替换分组" }

        val inputPath = input.toPath()

        val openResult = either<OpenError, MCTWorkspace> {
            MCTWorkspace(inputPath, env)
        }

        openResult.fold(
            ifLeft = { env.logger.error { it.message } },
            ifRight = { workspace ->
                when (mode) {
                    "region" -> {
                        val groups = all.filterIsInstance<RegionReplacementGroup>()
                        env.logger.info { "正在回填 ${groups.size} 个 Region 替换分组..." }
                        val result = either<MCTError, Unit> {
                            workspace.backfillRegion(groups)
                        }
                        result.fold(
                            ifLeft = { env.logger.error { it.message } },
                            ifRight = { env.logger.info { "Region 回填完成。" } }
                        )
                    }

                    "datapack" -> {
                        val groups = all.filterIsInstance<DatapackReplacementGroup>()
                        env.logger.info { "正在回填 ${groups.size} 个 Datapack 替换分组..." }
                        try {
                            workspace.backfillDatapack(groups)
                            env.logger.info { "Datapack 回填完成。" }
                        } catch (e: Exception) {
                            env.logger.error { e.message ?: "未知错误" }
                        }
                    }

                    else -> env.logger.error { "未知模式 $mode" }
                }
            }
        )
    }
}