@file:OptIn(ExperimentalMaterial3Api::class)

package mct.gui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import arrow.core.raise.either
import kotlinx.coroutines.launch
import mct.Env
import mct.onSign
import mct.util.translator.TranslateError
import mct.util.translator.TranslateSign
import okio.FileSystem

// ── 入口 ──────────────────────────────────────────────────────

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "MCT - Minecraft 翻译工具",
        state = WindowState(size = DpSize(820.dp, 760.dp))
    ) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                App()
            }
        }
    }
}

// ── 主框架 ────────────────────────────────────────────────────

@Composable
fun App() {
    var selectedTab by remember { mutableStateOf(0) }
    var logText by remember { mutableStateOf("就绪。\n") }
    var isRunning by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

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

    // ── Unified Env: single GuiLogger + single FileSystem ──────

    val guiLogger = remember { GuiLogger { logText += it } }
    val env = remember {
        Env(
            fs = FileSystem.SYSTEM,
            logger = guiLogger.onSign<TranslateSign> {
                when (it) {
                    is TranslateSign.Begin -> {
                        logText += "开始翻译，总的批数${it.batch}\n"
                    }

                    is TranslateSign.Progress -> {
                        translateProgress = it.progress
                        translateStatus = if (it.progress >= 1f) "完成" else "翻译中..."
                    }
                }
            }
        )
    }

    // 启动时同步加载 API 设置
    val savedSettings = remember { loadSettings() }
    LaunchedEffect(Unit) {
        apiUrl = savedSettings.apiUrl
        model = savedSettings.model
        apiToken = savedSettings.apiToken
        if (savedSettings.apiUrl.isNotBlank() || savedSettings.apiToken.isNotBlank()) {
            logText += "已加载 API 设置 ($settingsPathString)\n"
        }
    }

    val scope = rememberCoroutineScope()


    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        "MCT - Minecraft 翻译工具",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                navigationIcon = {
                    Icon(
                        Icons.Outlined.Translate,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            )

            SecondaryTabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                contentColor = MaterialTheme.colorScheme.surface,
                containerColor = MaterialTheme.colorScheme.primary,
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
            ) {
                Tab(selectedTab == 0, { selectedTab = 0 }) {
                    Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("提取文本")
                }
                Tab(selectedTab == 1, { selectedTab = 1 }) {
                    Icon(Icons.Outlined.Translate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("AI 翻译")
                }
                Tab(selectedTab == 2, { selectedTab = 2 }) {
                    Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("回填存档")
                }
            }

            // 内容区
            Card(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(.7f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                shape = MaterialTheme.shapes.large
            ) {
                AnimatedContent(
                    targetState = selectedTab,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        val dir = if (targetState > initialState) 1 else -1
                        (slideInHorizontally { w -> dir * w / 4 } + fadeIn()) togetherWith
                                (slideOutHorizontally { w -> -dir * w / 4 } + fadeOut())
                    },
                    label = "tab-content"
                ) { tab ->
                    val contentScroll = rememberScrollState()
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(contentScroll)) {
                        when (tab) {
                            0 -> ExtractPanel(
                                inputPath = extractInput, onInputChange = { extractInput = it },
                                outputPath = extractOutput, onOutputChange = { extractOutput = it },
                                mode = extractMode, onModeChange = { extractMode = it },
                                disableFilter = disableFilter, onDisableFilterChange = { disableFilter = it },
                                isRunning = isRunning,
                                onRun = {
                                    isRunning = true; logText = ""
                                    scope.launch {
                                        runExtraction(env, extractInput, extractOutput, extractMode, disableFilter)
                                        isRunning = false
                                    }
                                }
                            )

                            1 -> TranslatePanel(
                                inputPath = translateInput,
                                onInputChange = { translateInput = it },
                                outputPath = translateOutput,
                                onOutputChange = { translateOutput = it },
                                termOutput = termOutput,
                                onTermOutputChange = { termOutput = it },
                                apiUrl = apiUrl,
                                onApiUrlChange = { apiUrl = it },
                                apiToken = apiToken,
                                onApiTokenChange = { apiToken = it },
                                model = model,
                                onModelChange = { model = it },
                                existingTermPath = existingTermPath,
                                onExistingTermPathChange = { existingTermPath = it },
                                onSaveSettings = {
                                    if (saveSettings(apiUrl, model, apiToken))
                                        logText += "API 设置已保存到 $settingsPathString\n"
                                    else
                                        logText += "保存 API 设置失败\n"
                                },
                                translationProgress = translateProgress,
                                translationStatus = translateStatus,
                                isRunning = isRunning,
                                onRun = {
                                    isRunning = true; logText = ""; translateProgress = 0f; translateStatus = ""
                                    scope.launch {
                                        either {
                                            runTranslation(
                                                env,
                                                translateInput,
                                                translateOutput,
                                                termOutput,
                                                apiUrl.ifBlank { null },
                                                apiToken,
                                                model,
                                                existingTermPath.ifBlank { null }
                                            )
                                        }.onLeft {
                                            when (it) {
                                                TranslateError.IllegalUrl -> snackbarHostState.showSnackbar("The api url must end with /v1/.")
                                            }
                                        }
                                        isRunning = false
                                    }
                                }
                            )

                            2 -> BackfillPanel(
                                inputPath = backfillInput,
                                onInputChange = { backfillInput = it },
                                replacementsPath = backfillReplacements,
                                onReplacementsChange = { backfillReplacements = it },
                                mode = backfillMode,
                                onModeChange = { backfillMode = it },
                                isRunning = isRunning,
                                onRun = {
                                    isRunning = true; logText = ""
                                    scope.launch {
                                        runBackfill(env, backfillInput, backfillReplacements, backfillMode)
                                        isRunning = false
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 日志面板
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Outlined.Terminal,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "运行日志",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(4.dp))

                val logScroll = rememberScrollState()
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                    tonalElevation = 2.dp
                ) {
                    SelectionContainer {
                        Text(
                            text = logText,
                            modifier = Modifier.fillMaxSize().padding(10.dp).verticalScroll(logScroll),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
