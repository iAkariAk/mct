@file:OptIn(ExperimentalMaterial3Api::class)

package mct.gui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
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
import mct.LoggerLevel
import mct.extra.translator.TranslateSign
import mct.on
import mct.onSign
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
    var selectedTab by remember { mutableStateOf(Tab.Extract) }
    var logLines = remember { mutableStateListOf(LogEntry(null, "就绪。")) }
    var isRunning by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // 提取
    var extractState by remember { mutableStateOf(ExtractState()) }
    var translateState by remember { mutableStateOf(TranslateState()) }
    var backfillState by remember { mutableStateOf(BackfillState()) }

    var translateProgress by remember { mutableFloatStateOf(0f) }
    var translateStatus by remember { mutableStateOf("") }

    // 日志过滤
    var logLevelFilter by remember {
        mutableStateOf(setOf(LoggerLevel.Info, LoggerLevel.Warning, LoggerLevel.Error, LoggerLevel.Debug))
    }
    var showLogSettings by remember { mutableStateOf(false) }

    val guiLogger = remember {
        GuiLogger { entry -> logLines.add(entry) }.onSign {
            on<TranslateSign> { sign ->
                when (sign) {
                    is TranslateSign.Progress -> {
                        translateProgress = sign.progress
                        translateStatus = if (sign.progress >= 1f) "完成" else "翻译中..."
                    }
                }
            }
        }
    }
    val env = remember {
        Env(
            fs = FileSystem.SYSTEM,
            logger = guiLogger
        )
    }

    // 启动时同步加载 API 设置
    val savedSettings = remember { loadSettings() }
    LaunchedEffect(Unit) {
        translateState = translateState.copy(
            apiUrl = savedSettings.apiUrl,
            model = savedSettings.model,
            apiToken = savedSettings.apiToken
        )
        if (savedSettings.apiUrl.isNotBlank() || savedSettings.apiToken.isNotBlank()) {
            logLines.add(LogEntry(null, "已加载 API 设置 ($settingsPathString)"))
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
                selectedTabIndex = selectedTab.ordinal,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                contentColor = MaterialTheme.colorScheme.surface,
                containerColor = MaterialTheme.colorScheme.primary,
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
            ) {
                Tab(selectedTab == Tab.Extract, { selectedTab = Tab.Extract }) {
                    Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(Tab.Extract.label)
                }
                Tab(selectedTab == Tab.Translate, { selectedTab = Tab.Translate }) {
                    Icon(Icons.Outlined.Translate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(Tab.Translate.label)
                }
                Tab(selectedTab == Tab.Backfill, { selectedTab = Tab.Backfill }) {
                    Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(Tab.Backfill.label)
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
                            Tab.Extract -> ExtractPanel(
                                state = extractState,
                                onStateChange = { extractState = it },
                                isRunning = isRunning,
                                onRun = {
                                    isRunning = true; logLines.clear()
                                    scope.launch {
                                        runExtraction(
                                            env, extractState.input, extractState.output, extractState.mode.key,
                                            extractState.disableFilter,
                                            extractState.regionPatternPath, extractState.mcfPatternPath,
                                            extractState.mcjPatternPath
                                        )
                                        isRunning = false
                                    }
                                }
                            )

                            Tab.Translate -> TranslatePanel(
                                state = translateState,
                                onStateChange = { translateState = it },
                                translationProgress = translateProgress,
                                translationStatus = translateStatus,
                                isRunning = isRunning,
                                onRun = {
                                    isRunning = true
                                    logLines.clear()
                                    translateProgress = 0f
                                    translateStatus = ""
                                    scope.launch {
                                        either {
                                            runTranslation(
                                                env = env,
                                                input = translateState.input,
                                                output = translateState.output,
                                                mappingOutput = translateState.mappingOutput,
                                                termOutput = translateState.termOutput,
                                                apiUrl = translateState.apiUrl.ifBlank { null },
                                                token = translateState.apiToken,
                                                model = translateState.model,
                                                termPath = translateState.existingTermPath.ifBlank { null },
                                                useStreamApi = translateState.useStreamApi,
                                                literatureStyle = translateState.literatureStyle,
                                                onFailure = {
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar(it.message)
                                                    }
                                                }
                                            )
                                        }.onLeft {
                                            snackbarHostState.showSnackbar(it.message)
                                        }
                                        isRunning = false
                                    }
                                },
                                onSaveSettings = {
                                    logLines.add(LogEntry(null, if (saveSettings(translateState.apiUrl, translateState.model, translateState.apiToken))
                                        "API 设置已保存到 $settingsPathString"
                                    else
                                        "保存 API 设置失败"))
                                }
                            )

                            Tab.Backfill -> BackfillPanel(
                                state = backfillState,
                                onStateChange = { backfillState = it },
                                isRunning = isRunning,
                                onRun = {
                                    isRunning = true; logLines.clear()
                                    scope.launch {
                                        runBackfill(env, backfillState.input, backfillState.replacements, backfillState.mode.key)
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
                    Spacer(Modifier.weight(1f))
                    Box {
                        IconButton(onClick = { showLogSettings = true }) {
                            Icon(
                                Icons.Outlined.Settings,
                                contentDescription = "日志过滤",
                                modifier = Modifier.size(18.dp),
                                tint = if (logLevelFilter.size < 4) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = showLogSettings,
                            onDismissRequest = { showLogSettings = false }
                        ) {
                            Text(
                                "日志级别过滤",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            HorizontalDivider()
                            listOf(LoggerLevel.Info, LoggerLevel.Warning, LoggerLevel.Error, LoggerLevel.Debug).forEach { level ->
                                val checked = level in logLevelFilter
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(checked = checked, onCheckedChange = null)
                                            Spacer(Modifier.width(8.dp))
                                            Text(level.name)
                                        }
                                    },
                                    onClick = {
                                        logLevelFilter = if (checked) logLevelFilter - level
                                                         else logLevelFilter + level
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                val logScroll = rememberScrollState()
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                    tonalElevation = 2.dp
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        SelectionContainer {
                            Text(
                                text = coloredLogAnnotatedString(
                                    logLines.filter { it.level == null || it.level in logLevelFilter }
                                ),
                                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp).verticalScroll(logScroll),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            )
                        }
                        TextButton(
                            modifier = Modifier.align(Alignment.TopEnd),
                            onClick = {
                                scope.launch {
                                    logScroll.animateScrollTo(logScroll.maxValue)
                                }
                            }
                        ) {
                            Text("↓")
                        }
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
