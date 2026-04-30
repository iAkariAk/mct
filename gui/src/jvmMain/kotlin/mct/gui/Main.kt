package mct.gui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import arrow.core.raise.either
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mct.*
import mct.dp.backfillDatapack
import mct.dp.extractFromDatapack
import mct.region.BuiltinPatterns
import mct.region.backfillRegion
import mct.region.extractFromRegion
import mct.serializer.MCTJson
import mct.util.translator.OpenAITranslator
import mct.util.translator.TermTable
import mct.util.translator.TranslateSign
import mct.util.translator.translate
import okio.FileSystem
import okio.Path.Companion.toPath
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

// ── API 设置持久化 ──────────────────────────────────────────────

private val settingsDir = File(System.getProperty("user.home"), ".mct").also { it.mkdirs() }
private val settingsFile = File(settingsDir, "api-settings.json")
private val settingsJson = Json { ignoreUnknownKeys = true; encodeDefaults = false }

@Serializable
private data class ApiSettings(
    val apiUrl: String = "",
    val model: String = "gpt-4o",
    val apiToken: String = "",
)

private fun loadSettings(): ApiSettings {
    return try {
        if (settingsFile.exists())
            settingsJson.decodeFromString(settingsFile.readText(charset("UTF-8")))
        else ApiSettings()
    } catch (e: Exception) {
        println("[MCT] 加载API设置失败: ${e.message}")
        ApiSettings()
    }
}

private fun saveSettings(apiUrl: String, model: String, apiToken: String): Boolean {
    return try {
        settingsFile.writeText(settingsJson.encodeToString(ApiSettings(apiUrl, model, apiToken)), charset("UTF-8"))
        true
    } catch (e: Exception) {
        println("[MCT] 保存API设置失败: ${e.message}")
        false
    }
}

// ── 入口 ──────────────────────────────────────────────────────

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "MCT - Minecraft 翻译工具",
        state = WindowState(size = DpSize(780.dp, 720.dp))
    ) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            App()
        }
    }
}

// ── 主框架 ────────────────────────────────────────────────────

@Composable
fun App() {
    var selectedTab by remember { mutableStateOf(0) }
    var logText by remember { mutableStateOf("就绪。\n") }
    var isRunning by remember { mutableStateOf(false) }

    // 提取
    var extractInput by remember { mutableStateOf("") }
    var extractOutput by remember { mutableStateOf("extractions.json") }
    var extractMode by remember { mutableStateOf("region") }
    var disableFilter by remember { mutableStateOf(false) }

    // 翻译
    var translateInput by remember { mutableStateOf("extractions.json") }
    var translateOutput by remember { mutableStateOf("replacements.json") }
    var termOutput by remember { mutableStateOf("terms.json") }
    var apiUrl by remember { mutableStateOf("") }
    var apiToken by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("gpt-4o") }
    var existingTermPath by remember { mutableStateOf("") }
    var translateProgress by remember { mutableStateOf(0f) }
    var translateStatus by remember { mutableStateOf("") }

    // 回填
    var backfillInput by remember { mutableStateOf("") }
    var backfillReplacements by remember { mutableStateOf("replacements.json") }
    var backfillMode by remember { mutableStateOf("region") }

    // 启动时同步加载 API 设置
    val savedSettings = remember { loadSettings() }
    LaunchedEffect(Unit) {
        apiUrl = savedSettings.apiUrl
        model = savedSettings.model
        apiToken = savedSettings.apiToken
        if (savedSettings.apiUrl.isNotBlank() || savedSettings.apiToken.isNotBlank()) {
            logText += "已加载 API 设置 (${settingsFile.absolutePath})\n"
        }
    }

    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "MCT - Minecraft 翻译工具",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(8.dp))

        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(selectedTab == 0, { selectedTab = 0 }) { Text("① 提取文本") }
            Tab(selectedTab == 1, { selectedTab = 1 }) { Text("② AI 翻译") }
            Tab(selectedTab == 2, { selectedTab = 2 }) { Text("③ 回填存档") }
        }

        Spacer(Modifier.height(12.dp))

        // 内容区 - 可滚动
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.weight(1f, fill = true)
        ) {
            val contentScroll = rememberScrollState()
            Column(modifier = Modifier.fillMaxSize().verticalScroll(contentScroll)) {
                when (selectedTab) {
                    0 -> ExtractPanel(
                        inputPath = extractInput, onInputChange = { extractInput = it },
                        outputPath = extractOutput, onOutputChange = { extractOutput = it },
                        mode = extractMode, onModeChange = { extractMode = it },
                        disableFilter = disableFilter, onDisableFilterChange = { disableFilter = it },
                        isRunning = isRunning,
                        onRun = {
                            isRunning = true; logText = ""
                            scope.launch {
                                runExtraction(extractInput, extractOutput, extractMode, disableFilter) { logText += it }
                                isRunning = false
                            }
                        }
                    )

                    1 -> TranslatePanel(
                        inputPath = translateInput, onInputChange = { translateInput = it },
                        outputPath = translateOutput, onOutputChange = { translateOutput = it },
                        termOutput = termOutput, onTermOutputChange = { termOutput = it },
                        apiUrl = apiUrl, onApiUrlChange = { apiUrl = it },
                        apiToken = apiToken, onApiTokenChange = { apiToken = it },
                        model = model, onModelChange = { model = it },
                        existingTermPath = existingTermPath, onExistingTermPathChange = { existingTermPath = it },
                        onSaveSettings = {
                            if (saveSettings(apiUrl, model, apiToken))
                                logText += "API 设置已保存到 ${settingsFile.absolutePath}\n"
                            else
                                logText += "保存 API 设置失败\n"
                        },
                        translationProgress = translateProgress,
                        translationStatus = translateStatus,
                        isRunning = isRunning,
                        onRun = {
                            isRunning = true; logText = ""; translateProgress = 0f; translateStatus = ""
                            scope.launch {
                                runTranslation(
                                    translateInput, translateOutput, termOutput,
                                    apiUrl.ifBlank { null }, apiToken, model, existingTermPath.ifBlank { null },
                                    onLog = { logText += it },
                                    onProgress = { progress, status ->
                                        translateProgress = progress
                                        translateStatus = status
                                    }
                                )
                                isRunning = false
                            }
                        }
                    )

                    2 -> BackfillPanel(
                        inputPath = backfillInput, onInputChange = { backfillInput = it },
                        replacementsPath = backfillReplacements, onReplacementsChange = { backfillReplacements = it },
                        mode = backfillMode, onModeChange = { backfillMode = it },
                        isRunning = isRunning,
                        onRun = {
                            isRunning = true; logText = ""
                            scope.launch {
                                runBackfill(backfillInput, backfillReplacements, backfillMode) { logText += it }
                                isRunning = false
                            }
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Text("运行日志", style = MaterialTheme.typography.labelLarge)

        val logScroll = rememberScrollState()
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().weight(0.35f)
        ) {
            SelectionContainer {
                Text(
                    text = logText,
                    modifier = Modifier.fillMaxSize().padding(8.dp).verticalScroll(logScroll),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
            }
        }
    }
}

// ── 标签页 ①：提取 ────────────────────────────────────────────

@Composable
private fun ExtractPanel(
    inputPath: String, onInputChange: (String) -> Unit,
    outputPath: String, onOutputChange: (String) -> Unit,
    mode: String, onModeChange: (String) -> Unit,
    disableFilter: Boolean, onDisableFilterChange: (Boolean) -> Unit,
    isRunning: Boolean, onRun: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("输入 / 输出")

        PathRow("Minecraft 存档目录", "选择包含 level.dat 的文件夹...", inputPath, onInputChange) {
            chooseDirectory("选择存档目录")?.let(onInputChange)
        }
        PathRow("输出 JSON 文件", "选择保存位置...", outputPath, onOutputChange) {
            chooseSaveFile("extractions.json")?.let(onOutputChange)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        SectionTitle("提取选项")

        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            ModeRadio("Region (.mca 区域文件)", mode == "region") { onModeChange("region") }
            ModeRadio("Datapack (数据包)", mode == "datapack") { onModeChange("datapack") }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = disableFilter, onCheckedChange = onDisableFilterChange)
            Text("提取所有文本（禁用内置过滤器）")
        }

        Spacer(Modifier.height(4.dp))

        ActionButton("开始提取", isRunning, onRun, enabled = inputPath.isNotBlank() && outputPath.isNotBlank())
    }
}

// ── 标签页 ②：AI 翻译 ─────────────────────────────────────────

@Composable
private fun TranslatePanel(
    inputPath: String, onInputChange: (String) -> Unit,
    outputPath: String, onOutputChange: (String) -> Unit,
    termOutput: String, onTermOutputChange: (String) -> Unit,
    apiUrl: String, onApiUrlChange: (String) -> Unit,
    apiToken: String, onApiTokenChange: (String) -> Unit,
    model: String, onModelChange: (String) -> Unit,
    existingTermPath: String, onExistingTermPathChange: (String) -> Unit,
    onSaveSettings: () -> Unit,
    translationProgress: Float, translationStatus: String,
    isRunning: Boolean, onRun: () -> Unit
) {
    var showToken by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("输入 / 输出")

        PathRow("提取结果 JSON（来自步骤①）", "选择 extractions.json...", inputPath, onInputChange) {
            chooseOpenFile("extractions.json")?.let(onInputChange)
        }
        PathRow("输出替换文件 JSON", "选择保存位置...", outputPath, onOutputChange) {
            chooseSaveFile("replacements.json")?.let(onOutputChange)
        }
        PathRow("输出术语表 JSON", "选择保存位置...", termOutput, onTermOutputChange) {
            chooseSaveFile("terms.json")?.let(onTermOutputChange)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        SectionTitle("AI API 配置")
        Text(
            "支持 OpenAI 及所有兼容接口（如 APIHub、OneAPI、LobeHub 等）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = apiUrl,
            onValueChange = onApiUrlChange,
            label = { Text("API 地址") },
            placeholder = { Text("留空使用 OpenAI 官方；或填入 https://api.openai.com/v1") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = model,
            onValueChange = onModelChange,
            label = { Text("模型名称") },
            placeholder = { Text("例如 gpt-4o, gpt-4o-mini, deepseek-chat...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = apiToken,
            onValueChange = onApiTokenChange,
            label = { Text("API 密钥") },
            placeholder = { Text("sk-...") },
            singleLine = true,
            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { showToken = !showToken }) {
                    Text(if (showToken) "隐藏" else "显示")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        SectionTitle("可选设置")

        PathRow("已有术语表 JSON（可选）", "留空则从头翻译...", existingTermPath, onExistingTermPathChange) {
            chooseOpenFile("terms.json")?.let(onExistingTermPathChange)
        }

        // 翻译进度
        if (isRunning || translationProgress > 0f) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("翻译进度", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "${(translationProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                LinearProgressIndicator(
                    progress = { translationProgress },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                )
                if (translationStatus.isNotBlank()) {
                    Text(
                        translationStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onSaveSettings,
                enabled = model.isNotBlank() && apiToken.isNotBlank(),
                modifier = Modifier.weight(0.35f).height(44.dp)
            ) {
                Text("保存 API 设置")
            }
            Box(modifier = Modifier.weight(0.65f)) {
                ActionButton(
                    "开始 AI 翻译",
                    isRunning,
                    onRun,
                    enabled = inputPath.isNotBlank() && outputPath.isNotBlank()
                            && termOutput.isNotBlank() && model.isNotBlank() && apiToken.isNotBlank()
                )
            }
        }
    }
}

// ── 标签页 ③：回填 ────────────────────────────────────────────

@Composable
private fun BackfillPanel(
    inputPath: String, onInputChange: (String) -> Unit,
    replacementsPath: String, onReplacementsChange: (String) -> Unit,
    mode: String, onModeChange: (String) -> Unit,
    isRunning: Boolean, onRun: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("输入 / 输出")

        PathRow("Minecraft 存档目录", "选择包含 level.dat 的文件夹...", inputPath, onInputChange) {
            chooseDirectory("选择存档目录")?.let(onInputChange)
        }
        PathRow("替换文件 JSON（来自步骤②）", "选择 replacements.json...", replacementsPath, onReplacementsChange) {
            chooseOpenFile("replacements.json")?.let(onReplacementsChange)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        SectionTitle("回填模式")

        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            ModeRadio("Region (.mca 区域文件)", mode == "region") { onModeChange("region") }
            ModeRadio("Datapack (数据包)", mode == "datapack") { onModeChange("datapack") }
        }

        Text(
            "⚠ 回填会直接修改存档文件，建议操作前备份！",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )

        Spacer(Modifier.height(4.dp))

        ActionButton(
            "开始回填",
            isRunning,
            onRun,
            enabled = inputPath.isNotBlank() && replacementsPath.isNotBlank()
        )
    }
}

// ── 可复用组件 ───────────────────────────────────────────────

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun PathRow(
    label: String, placeholder: String,
    value: String, onValueChange: (String) -> Unit,
    onBrowse: () -> Unit
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text(placeholder) }
            )
            Button(onClick = onBrowse) { Text("浏览") }
        }
    }
}

@Composable
private fun ModeRadio(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}

@Composable
private fun ActionButton(label: String, running: Boolean, onClick: () -> Unit, enabled: Boolean) {
    Button(
        onClick = onClick,
        enabled = enabled && !running,
        modifier = Modifier.fillMaxWidth().height(44.dp)
    ) {
        if (running) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(if (running) "运行中..." else label)
    }
}

// ── 文件选择器 ──────────────────────────────────────────────

private fun chooseDirectory(title: String): String? {
    val chooser = JFileChooser().apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogTitle = title
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION)
        chooser.selectedFile.absolutePath else null
}

private fun chooseSaveFile(defaultName: String): String? {
    val chooser = JFileChooser().apply {
        fileSelectionMode = JFileChooser.FILES_ONLY
        dialogTitle = "保存文件"
        fileFilter = FileNameExtensionFilter("JSON 文件", "json")
        selectedFile = File(defaultName)
    }
    return if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
        val file = chooser.selectedFile
        if (file.extension.isEmpty()) file.absolutePath + ".json" else file.absolutePath
    } else null
}

private fun chooseOpenFile(defaultName: String): String? {
    val chooser = JFileChooser().apply {
        fileSelectionMode = JFileChooser.FILES_ONLY
        dialogTitle = "打开文件"
        fileFilter = FileNameExtensionFilter("JSON 文件", "json")
        selectedFile = File(defaultName)
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION)
        chooser.selectedFile.absolutePath else null
}

// ── 后台任务 ─────────────────────────────────────────────────

private fun guiLogger(appendLog: (String) -> Unit) = object : Logger(LoggerLevel.Verbose) {
    override fun log(level: LoggerLevel, message: String) {
        if (level == LoggerLevel.Sign) return
        appendLog("[$level] $message\n")
    }
}

// -- 提取 --

private suspend fun runExtraction(
    input: String, output: String, mode: String, disableFilter: Boolean,
    appendLog: (String) -> Unit
) {
    withContext(Dispatchers.IO) {
        appendLog("正在打开: $input\n")
        val inputPath = input.toPath()
        val env = Env(fs = FileSystem.SYSTEM, logger = guiLogger(appendLog))

        @Suppress("UNCHECKED_CAST")
        val result = either<MCTError, List<ExtractionGroup>> {
            val workspace = MCTWorkspace(inputPath, env)
            when (mode) {
                "region" -> {
                    val patterns = if (disableFilter) null else BuiltinPatterns.toList()
                    workspace.extractFromRegion(patterns = patterns).toList() as List<ExtractionGroup>
                }

                "datapack" -> {
                    val mcjPatterns: List<mct.pointer.DataPointerPattern>? =
                        if (disableFilter) null else emptyList()
                    workspace.extractFromDatapack(mcjPatterns = mcjPatterns).toList() as List<ExtractionGroup>
                }

                else -> error("未知模式: $mode")
            }
        }

        result.fold(
            ifLeft = { appendLog("错误: ${it.message}\n") },
            ifRight = { groups ->
                val total = groups.sumOf { it.extractions.size }
                appendLog("提取了 ${groups.size} 个分组, 共 $total 条文本\n")
                File(output).writeText(MCTJson.encodeToString(groups))
                appendLog("已写入: $output\n完成。\n")
            }
        )
    }
}

// -- 翻译 --

private suspend fun runTranslation(
    input: String, output: String, termOutput: String,
    apiUrl: String?, token: String, model: String, termPath: String?,
    onLog: (String) -> Unit,
    onProgress: (Float, String) -> Unit
) {
    withContext(Dispatchers.IO) {
        onLog("正在加载提取结果: $input\n")
        val json = File(input).readText()
        val extractionGroups = MCTJson.decodeFromString<List<ExtractionGroup>>(json)
        onLog("已加载 ${extractionGroups.size} 个提取分组\n")

        val existingTerms: TermTable = if (termPath != null && File(termPath).exists()) {
            MCTJson.decodeFromString<TermTable>(File(termPath).readText()).also {
                onLog("已加载 ${it.size} 个已有术语\n")
            }
        } else emptySet()

        val env = Env(fs = FileSystem.SYSTEM, logger = guiLogger(onLog).onSign<TranslateSign> {
            when (it) {
                is TranslateSign.Begin -> onLog("开始翻译，总的批数${it.batch}")
                is TranslateSign.Progress -> onProgress(it.progress, "OK")
            }
        })
        val translator = OpenAITranslator(
            apiUrl?.trim()?.ifBlank { null },
            token.trim(),
            model.trim(),
            existingTerms,
            env
        )
        try {
            val replacements = translator.translate(extractionGroups)

            File(output).writeText(MCTJson.encodeToString(replacements))
            File(termOutput).writeText(MCTJson.encodeToString(translator.terms))

//            onLog("已翻译 ${mapping.size} 条文本, 共 ${extractionGroups.sumOf { it.extractions.size }} 条原文\n")
            onLog("新发现 ${translator.terms.size - existingTerms.size} 个术语\n")
            onLog("替换文件已写入: $output\n")
            onLog("术语表已写入: $termOutput\n完成。\n")
            saveSettings(apiUrl ?: "", model, token)
        } catch (e: Exception) {
            onLog("翻译出错: ${e.message}\n")
        }
    }
}

// -- 回填 --

private suspend fun runBackfill(
    input: String, replacementsFile: String, mode: String,
    appendLog: (String) -> Unit
) {
    withContext(Dispatchers.IO) {
        appendLog("正在打开存档: $input\n")
        appendLog("正在加载替换文件: $replacementsFile\n")

        val json = File(replacementsFile).readText()
        val all = MCTJson.decodeFromString<List<ReplacementGroup>>(json)
        appendLog("已加载 ${all.size} 个替换分组\n")

        val inputPath = input.toPath()
        val env = Env(fs = FileSystem.SYSTEM, logger = guiLogger(appendLog))

        val openResult = either<OpenError, MCTWorkspace> {
            MCTWorkspace(inputPath, env)
        }

        openResult.fold(
            ifLeft = { appendLog("错误: ${it.message}\n") },
            ifRight = { workspace ->
                when (mode) {
                    "region" -> {
                        val groups = all.filterIsInstance<RegionReplacementGroup>()
                        appendLog("正在回填 ${groups.size} 个 Region 替换分组...\n")
                        val result = either<MCTError, Unit> {
                            workspace.backfillRegion(groups)
                        }
                        result.fold(
                            ifLeft = { appendLog("错误: ${it.message}\n") },
                            ifRight = { appendLog("Region 回填完成。\n") }
                        )
                    }

                    "datapack" -> {
                        val groups = all.filterIsInstance<DatapackReplacementGroup>()
                        appendLog("正在回填 ${groups.size} 个 Datapack 替换分组...\n")
                        try {
                            workspace.backfillDatapack(groups)
                            appendLog("Datapack 回填完成。\n")
                        } catch (e: Exception) {
                            appendLog("错误: ${e.message}\n")
                        }
                    }

                    else -> appendLog("错误: 未知模式 $mode\n")
                }
            }
        )
    }
}
