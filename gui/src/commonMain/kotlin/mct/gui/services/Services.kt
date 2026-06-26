package mct.gui.services

import arrow.core.raise.Raise
import arrow.core.raise.either
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import mct.*
import mct.command.BuiltinCommandDataPatterns
import mct.command.BuiltinCommandPatterns
import mct.command.CommandExtractPattern
import mct.command.CommandRegexPattern
import mct.dp.backfillDatapack
import mct.dp.compile
import mct.dp.extractFromDatapack
import mct.extra.ai.ChatCompletionCallError
import mct.extra.ai.TOKEN_COUNT_THRESHOLD
import mct.extra.ai.translator.*
import mct.gui.model.GuiSettings
import mct.gui.model.LogEntry
import mct.gui.util.setting
import mct.kit.exportIntoPool
import mct.kit.exportRegionSnbt
import mct.model.patch.*
import mct.nbt.BuiltinNbtPatterns
import mct.pointer.CustomizedDataPointerPattern
import mct.pointer.DataPointer
import mct.pointer.DataPointerPattern
import mct.pointer.matches
import mct.region.backfillRegion
import mct.region.extractFromRegion
import mct.serializer.MCTJson
import mct.util.io.writeJson
import okio.Path.Companion.toPath
import mct.dp.mcjson.BuiltinMCJPatterns as MCJBuiltinPatterns


@Serializable
data class ApiSettings(
    val apiUrl: String = "",
    val model: String = "gpt-4o",
    val apiToken: String = "",
    val useStreamApi: Boolean = false,
    val tokenThreshold: Int = TOKEN_COUNT_THRESHOLD,
    val temperature: Double? = null,
    val concurrency: Int = 1,
    val concurrentByKind: Boolean = false,
)

val apiSetting = setting<ApiSettings>("api-settings", ::ApiSettings)

@Serializable
data class ThemeSettings(
    val seedColorArgb: Int = 0,
)

val themeSetting = setting<ThemeSettings>("theme-settings", ::ThemeSettings)

// ---- 统一日志器 ---------------------------------------------------------

/**
 * A [Logger] implementation for the GUI layer.
 * - Non-Sign messages (Info/Debug/Error/Warning) are forwarded to [onLog] with level prefix.
 * - Sign messages are silently dropped (they are intercepted upstream by `onSign<>` wrappers).
 */
class GuiLogger(
    private val onLog: (LogEntry) -> Unit,
) : Logger(LoggerLevel.Verbose) {
    override fun log(level: LoggerLevel, message: String) {
        println(message)
        onLog(LogEntry(level, message))
    }
}

// ---- 后台任务 -----------------------------------------------------------

/**
 * Run extraction in the background.
 */
context(env: Env)
suspend fun runExtraction(
    input: String,
    output: String,
    mode: String,
    disableFilter: Boolean,
    regionPatternPath: String = "",
    commandPatternPath: String = "",
    commandDataPatternPath: String = "",
    mcjPatternPath: String = "",
    commandRegexPatternPath: String = "",
) {
    withContext(Dispatchers.IO) {
        env.logger.info { "正在打开: $input" }
        val inputPath = input.toPath()

        @Suppress("UNCHECKED_CAST")
        val result = either {
            val workspace = MCTWorkspace(inputPath, env)
            val commandRegexPatterns: List<CommandRegexPattern> = commandRegexPatternPath.takeIf { it.isNotBlank() }
                ?.let { p ->
                    env.fs.read(p.toPath()) { readUtf8() }
                        .let { MCTJson.decodeFromString<List<CommandRegexPattern>>(it) }
                } ?: emptyList()
            when (mode) {
                "region" -> {
                    val patterns = if (disableFilter) null
                    else {
                        val userPatterns = regionPatternPath.takeIf { it.isNotBlank() }
                            ?.let { p ->
                                env.fs.read(p.toPath()) { readUtf8() }
                                    .let { MCTJson.decodeFromString<List<CustomizedDataPointerPattern>>(it) }
                                    .map { it.compile() }
                            }
                        if (userPatterns != null) BuiltinNbtPatterns.toList() + userPatterns
                        else BuiltinNbtPatterns.toList()
                    }

                    val commandPatterns = commandPatternPath.takeIf { it.isNotBlank() }
                        ?.let { p ->
                            env.fs.read(p.toPath()) { readUtf8() }
                                .let { MCTJson.decodeFromString<List<CommandExtractPattern>>(it) }
                                .compile()
                        } ?: BuiltinCommandPatterns

                    val commandDataPatterns: List<DataPointerPattern>? =
                        if (disableFilter) null
                        else {
                            val userPatterns = commandDataPatternPath.takeIf { it.isNotBlank() }
                                ?.let { p ->
                                    env.fs.read(p.toPath()) { readUtf8() }
                                        .let { MCTJson.decodeFromString<List<CustomizedDataPointerPattern>>(it) }
                                        .map { it.compile() }
                                }
                            if (userPatterns != null) BuiltinCommandDataPatterns + userPatterns
                            else BuiltinCommandDataPatterns
                        }

                    workspace.extractFromRegion(
                        MCTPattern(
                            nbt = patterns,
                            command = commandPatterns,
                            commandData = commandDataPatterns,
                            commandRegex = commandRegexPatterns
                        )
                    ).toList() as List<ExtractionGroup>
                }

                "datapack" -> {
                    val commandPatterns = commandPatternPath.takeIf { it.isNotBlank() }
                        ?.let { p ->
                            env.fs.read(p.toPath()) { readUtf8() }
                                .let { MCTJson.decodeFromString<List<CommandExtractPattern>>(it) }
                                .compile()
                        } ?: BuiltinCommandPatterns

                    val commandDataPatterns: List<DataPointerPattern>? = if (disableFilter) null
                    else {
                        val userPatterns = commandDataPatternPath.takeIf { it.isNotBlank() }
                            ?.let { p ->
                                env.fs.read(p.toPath()) { readUtf8() }
                                    .let { MCTJson.decodeFromString<List<CustomizedDataPointerPattern>>(it) }
                                    .map { it.compile() }
                            }
                        if (userPatterns != null) BuiltinCommandDataPatterns + userPatterns
                        else BuiltinCommandDataPatterns
                    }

                    val mcjPatterns: List<DataPointerPattern>? =
                        if (disableFilter) null
                        else {
                            val userPatterns = mcjPatternPath.takeIf { it.isNotBlank() }
                                ?.let { p ->
                                    env.fs.read(p.toPath()) { readUtf8() }
                                        .let { MCTJson.decodeFromString<List<CustomizedDataPointerPattern>>(it) }
                                        .map { it.compile() }
                                }
                            if (userPatterns != null) MCJBuiltinPatterns + userPatterns
                            else MCJBuiltinPatterns
                        }
                    workspace.extractFromDatapack(
                        MCTPattern(
                            command = commandPatterns,
                            commandData = commandDataPatterns,
                            mcjson = mcjPatterns,
                            commandRegex = commandRegexPatterns
                        )
                    ).toList() as List<ExtractionGroup>
                }

                else -> error("未知模式: $mode")
            }
        }

        result.fold(
            ifLeft = { env.logger.error { it.message } },
            ifRight = { groups ->
                val total = groups.sumOf { it.extractions.size }
                env.logger.info { "提取了 ${groups.size} 个分组, 共 $total 条文本" }
                output.toPath().writeJson(groups, pretty = GuiSettings.prettyOutput)
                env.logger.info { "已写入: $output" }
                env.logger.info { "完成。" }
            }
        )
    }
}

/**
 * Run AI translation in the background.
 */
context(env: Env, _: Raise<ChatCompletionCallError>)
suspend fun runTranslation(
    clientManager: ClientManager,
    input: String,
    cachesPath: String?,
    output: String,
    mappingOutput: String,
    termOutput: String,
    apiUrl: String?,
    token: String,
    model: String,
    termPath: String?,
    literatureStyle: String = CustomizedPrompts.literatureStyle,
    targetLanguage: String = CustomizedPrompts.targetLanguage,
    handleGradientAggressively: Boolean = CustomizedPrompts.handleGradientAggressively,
    temperature: Double? = null,
    concurrency: Int = GuiSettings.concurrency,
    onFailure: ((ChatCompletionCallError) -> Unit)? = null,
    onCancel: OnTranslateCancel = { _, _ -> },
) {
    env.logger.info { "正在加载提取结果: $input" }

    val (extractionGroups, existingTerms, caches) = withContext(Dispatchers.IO) {
        val json = env.fs.read(input.toPath()) { readUtf8() }
        val groups = MCTJson.decodeFromString<List<ExtractionGroup>>(json)
        env.logger.info { "已加载 ${groups.size} 个提取分组" }

        val terms: TermTable = if (termPath != null && env.fs.exists(termPath.toPath())) {
            val termJson = env.fs.read(termPath.toPath()) { readUtf8() }
            MCTJson.decodeFromString<TermTable>(termJson).also {
                env.logger.info { "已加载 ${it.size} 个已有术语" }
            }
        } else emptySet()

        val caches = if (cachesPath != null && env.fs.exists(cachesPath.toPath())) {
            val cacheJson = env.fs.read(cachesPath.toPath()) { readUtf8() }
            MCTJson.decodeFromString<Map<String, String>>(cacheJson).also {
                env.logger.info { "已加载 ${it.size} 个已有术语" }
            }
        } else emptyMap()

        Triple(groups, terms, caches)
    }

    val call = clientManager.chatCompletionCall
    if (call == null) {
        onFailure?.invoke(ChatCompletionCallError.UnvalidatedApi("没有 API 连接，请先在设置中配置"))
        return
    }

    val translator = Translator(
        call = call,
        defaultTerms = existingTerms,
        customizedPrompts = CustomizedPrompts(
            literatureStyle = literatureStyle,
            targetLanguage = targetLanguage,
            handleGradientAggressively = handleGradientAggressively,
        ),
        tokenThreshold = GuiSettings.tokenThreshold,
        concurrency = concurrency,
    )

    val wrappedOnCancel: OnTranslateCancel = { terms, salvaged ->
        runCatching {
            mappingOutput.toPath().writeJson(salvaged, pretty = GuiSettings.prettyOutput)
            termOutput.toPath().writeJson(terms, pretty = GuiSettings.prettyOutput)
            env.logger.info { "已保存 ${salvaged.size} 条部分映射到 $mappingOutput" }
            env.logger.info { "已保存 ${terms.size} 条术语到 $termOutput" }
        }
        onCancel(terms, salvaged)
    }

    withContext(Dispatchers.IO) {
        try {
            val mapping = translator.translate(
                extractionGroups,
                caches,
                concurrentByKind = GuiSettings.concurrentByKind,
                onCancel = wrappedOnCancel
            )
            val replacements = extractionGroups.replace(mapping)

            output.toPath().writeJson(replacements, pretty = GuiSettings.prettyOutput)
            mappingOutput.toPath().writeJson(mapping, pretty = GuiSettings.prettyOutput)
            termOutput.toPath().writeJson(translator.terms, pretty = GuiSettings.prettyOutput)

            env.logger.info { "新发现 ${translator.terms.size - existingTerms.size} 个术语" }
            env.logger.info { "替换文件已写入: $output" }
            env.logger.info { "映射文件已写入: $mappingOutput" }
            env.logger.info { "术语表已写入: $termOutput" }
            env.logger.info { "完成。" }
            apiSetting.save(
                ApiSettings(
                    apiUrl = apiUrl ?: "",
                    model = model,
                    apiToken = token,
                    useStreamApi = GuiSettings.useStreamApi,
                    tokenThreshold = GuiSettings.tokenThreshold,
                    temperature = temperature,
                    concurrency = concurrency,
                    concurrentByKind = GuiSettings.concurrentByKind,
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            env.logger.error { e.stackTraceToString() }
        }
    }
}

/**
 * Run backfill in the background.
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

        val openResult = either {
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

// ── 术语提取 ──────────────────────────────────────────────────

/**
 * Run AI term extraction in the background.
 *
 * Loads [input] (extractions.json), converts it to a text pool,
 * runs [TermExtractor] via the already-configured [clientManager.chatCompletionCall],
 * and writes the extracted [TermTable] to [output].
 */
context(env: Env)
suspend fun runTermExtraction(
    clientManager: ClientManager,
    input: String,
    output: String,
    termPath: String?,
    targetLanguage: String = CustomizedPrompts.targetLanguage,
    onCancel: OnTermExtractCancel = {},
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
        env.logger.error { "没有 API 连接，请先在设置中配置" }
        return
    }

    val textPool = extractionGroups.exportIntoPool(simply = false)
    env.logger.info { "共 ${textPool.size} 个待提取文本" }

    val extractor = TermExtractor(
        call = call,
        tokenThreshold = GuiSettings.tokenThreshold,
        targetLanguage = targetLanguage,
        concurrency = GuiSettings.concurrency,
        defaultTerms = existingTerms,
    )

    withContext(Dispatchers.IO) {
        try {
            val result = either {
                extractor.extract(textPool) { partialTerms ->
                    env.logger.info { "提取被取消，已保存 ${partialTerms.size} 条术语" }
                    runCatching {
                        output.toPath().writeJson(partialTerms, pretty = GuiSettings.prettyOutput)
                    }
                    onCancel(partialTerms)
                }
            }

            result.fold(
                ifLeft = { error -> env.logger.error { "提取失败: ${error.message}" } },
                ifRight = { terms ->
                    val newTerms = terms.size - existingTerms.size
                    output.toPath().writeJson(terms, pretty = GuiSettings.prettyOutput)
                    env.logger.info { "提取了 ${terms.size} 条术语（新增 $newTerms）" }
                    env.logger.info { "术语表已写入: $output" }
                    env.logger.info { "完成。" }
                },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            env.logger.error { e.stackTraceToString() }
        }
    }
}

// ── 工具箱服务 ────────────────────────────────────────────────

/**
 * Test whether a DataPointer string matches the built-in (+ optional custom) filter patterns.
 */
context(env: Env)
suspend fun runPointerTest(
    kind: String,
    patternPath: String?,
    noBuiltin: Boolean,
    pointerStr: String,
): Boolean = withContext(Dispatchers.IO) {
    val extra = if (patternPath != null) {
        env.fs.read(patternPath.toPath()) { readUtf8() }
            .let { MCTJson.decodeFromString<List<CustomizedDataPointerPattern>>(it) }
            .map { it.compile() }
    } else emptyList()

    val builtin: List<DataPointerPattern> = when (kind) {
        "mcjson" -> MCJBuiltinPatterns
        "region" -> BuiltinNbtPatterns.toList()
        else -> error("未知 kind: $kind")
    }
    val patterns = if (noBuiltin) extra else builtin + extra

    val result = decodePointerSafely(pointerStr, patterns)
    env.logger.info { "Pointer 测试: $pointerStr → $result (kind=$kind, patterns=${patterns.size})" }
    result
}

private fun decodePointerSafely(pointerStr: String, patterns: List<DataPointerPattern>): Boolean = try {
    val pointer = MCTJson.decodeFromString<DataPointer>(MCTJson.encodeToString(pointerStr))
    pointer.matches(patterns)
} catch (_: Exception) {
    false
}

/**
 * Export all region NBT data as SNBT text files to the given directory.
 */
context(env: Env)
suspend fun runExportSnbt(
    input: String,
    output: String,
) {
    withContext(Dispatchers.IO) {
        env.logger.info { "正在打开存档: $input" }
        val inputPath = input.toPath()
        val outputPath = output.toPath()

        either {
            MCTWorkspace(inputPath, env)
        }.fold(
            ifLeft = { env.logger.error { it.message } },
            ifRight = { workspace ->
                env.logger.info { "正在导出 SNBT 到: $output" }
                workspace.exportRegionSnbt(outputPath)
                env.logger.info { "SNBT 导出完成。" }
            }
        )
    }
}
