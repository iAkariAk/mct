package mct.gui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import arrow.core.getOrElse
import arrow.core.raise.either
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mct.Env
import mct.LoggerLevel
import mct.extra.ai.AiSign
import mct.extra.ai.ChatCompletionCall
import mct.extra.ai.createOpenAIClient
import mct.extra.ai.translator.TranslateSign
import mct.extra.ai.translator.optimizePrompt
import mct.on
import mct.onSign
import okio.FileSystem
import org.koin.compose.koinInject
import org.koin.core.context.startKoin

fun main() = application {
    startKoin {
        modules(apiModule)
    }

    val state = rememberWindowState(size = DpSize(820.dp, 760.dp))
    Window(
        onCloseRequest = ::exitApplication,
        state = state,
        undecorated = true,
        transparent = true,
    ) {
        LaunchedEffect(Unit) { window.minimumSize = java.awt.Dimension(400, 300) }

        MaterialTheme(colorScheme = darkColorScheme()) {
            Surface(
                modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(Modifier.fillMaxSize()) {
                    WindowTitleBar(state, onCloseRequest = ::exitApplication)
                    App(Modifier.weight(1f))
                }
            }
        }
    }
}

// ── 主框架 ────────────────────────────────────────────────────

@Composable
fun App(modifier: Modifier = Modifier) {
    val clientManager = koinInject<ClientManager>()
    var selectedTab by remember { mutableStateOf(Tab.Extract) }
    val logLines = remember { mutableStateListOf(LogEntry(null, "就绪。")) }
    var isRunning by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    var extractState by remember { mutableStateOf(ExtractState()) }
    var translateState by remember { mutableStateOf(TranslateState()) }
    var backfillState by remember { mutableStateOf(BackfillState()) }

    var translateProgress by remember { mutableFloatStateOf(0f) }
    var translateStatus by remember { mutableStateOf("") }
    var lastTokenConsume by remember { mutableIntStateOf(0) }
    var totalTokenConsume by remember { mutableIntStateOf(0) }

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
            on<AiSign> { sign ->
                when (sign) {
                    is AiSign.ConsumeToken -> {
                        lastTokenConsume = sign.count
                        totalTokenConsume += sign.count
                    }
                }
            }
        }
    }
    val env = remember {
        Env(fs = FileSystem.SYSTEM, logger = guiLogger)
    }

    context(env) {
        val savedSettings = remember { loadSettings() }
        LaunchedEffect(Unit) {
            translateState = translateState.copy(
                apiUrl = savedSettings.apiUrl, model = savedSettings.model, apiToken = savedSettings.apiToken
            )
            if (savedSettings.apiUrl.isNotBlank() || savedSettings.apiToken.isNotBlank()) {
                logLines.add(LogEntry(null, "已加载 API 设置 ($settingsPathString)"))
                try {
                    clientManager.openAIClient =
                        createOpenAIClient(savedSettings.apiUrl.ifBlank { null }, savedSettings.apiToken)
                    logLines.add(LogEntry(LoggerLevel.Debug, "正在请求可用模型列表..."))
                    val models = clientManager.openAIClient!!.listModels()
                    translateState = translateState.copy(availableModels = models)
                    logLines.add(LogEntry(LoggerLevel.Debug, "可用模型: ${models.joinToString(", ")}"))
                    logLines.add(LogEntry(null, "已获取 ${models.size} 个可用模型"))

                    if (savedSettings.model in models) {
                        either {
                            clientManager.chatCompletionCall = ChatCompletionCall(
                                client = clientManager.openAIClient!!,
                                model = savedSettings.model,
                            )
                        }.onLeft {
                            logLines.add(LogEntry(LoggerLevel.Warning, "创建 API 连接失败: ${it.message}"))
                        }
                    }
                } catch (e: Exception) {
                    logLines.add(LogEntry(LoggerLevel.Warning, "获取模型列表失败: ${e.message}"))
                }
            }
        }

        LaunchedEffect(translateState.apiUrl, translateState.apiToken) {
            clientManager.chatCompletionCall = null
            if (translateState.apiUrl.isNotBlank() && translateState.apiToken.isNotBlank()) {
                translateState = translateState.copy(isModelsLoading = true)
                try {
                    clientManager.openAIClient =
                        createOpenAIClient(translateState.apiUrl.ifBlank { null }, translateState.apiToken)
                    logLines.add(LogEntry(LoggerLevel.Debug, "正在刷新模型列表..."))
                    val models = clientManager.openAIClient!!.listModels()
                    translateState = translateState.copy(availableModels = models, isModelsLoading = false)
                    logLines.add(LogEntry(LoggerLevel.Debug, "可用模型: ${models.joinToString(", ")}"))

                    if (translateState.model in models) {
                        either {
                            clientManager.chatCompletionCall = ChatCompletionCall(
                                client = clientManager.openAIClient!!,
                                model = translateState.model,
                            )
                        }.onLeft {
                            logLines.add(LogEntry(LoggerLevel.Warning, "创建 API 连接失败: ${it.message}"))
                        }
                    }
                } catch (_: Exception) {
                    translateState = translateState.copy(availableModels = emptyList(), isModelsLoading = false)
                }
            }
        }

        LaunchedEffect(translateState.model) {
            if (clientManager.openAIClient != null && translateState.model.isNotBlank()) {
                val models = translateState.availableModels
                if (models.isEmpty() || translateState.model in models) {
                    either {
                        clientManager.chatCompletionCall = ChatCompletionCall(
                            client = clientManager.openAIClient!!,
                            model = translateState.model,
                        )
                    }.onLeft {
                        logLines.add(LogEntry(LoggerLevel.Warning, "切换模型失败: ${it.message}"))
                    }
                }
            }
        }

        val scope = rememberCoroutineScope()

        fun launchOp(prelude: () -> Unit, block: suspend CoroutineScope.() -> Unit) {
            prelude()
            scope.launch {
                try {
                    block()
                } catch (e: Exception) {
                    logLines.add(LogEntry(LoggerLevel.Error, e.stackTraceToString()))
                    snackbarHostState.showSnackbar(e.message ?: "未知错误")
                } finally {
                    isRunning = false
                }
            }
        }

        Box(modifier = modifier.fillMaxSize().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    header = {
                        Spacer(Modifier.height(8.dp))
                        Icon(
                            Icons.Outlined.Translate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                    },
                    modifier = Modifier.padding(end = 4.dp),
                    containerColor = Color.Transparent,
                ) {
                    NavigationRailItem(
                        selected = selectedTab == Tab.Extract,
                        onClick = { selectedTab = Tab.Extract },
                        icon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        label = { Text(Tab.Extract.label, style = MaterialTheme.typography.labelSmall) })
                    NavigationRailItem(
                        selected = selectedTab == Tab.Translate,
                        onClick = { selectedTab = Tab.Translate },
                        icon = { Icon(Icons.Outlined.Translate, contentDescription = null) },
                        label = { Text(Tab.Translate.label, style = MaterialTheme.typography.labelSmall) })
                    NavigationRailItem(
                        selected = selectedTab == Tab.Backfill,
                        onClick = { selectedTab = Tab.Backfill },
                        icon = { Icon(Icons.Outlined.Restore, contentDescription = null) },
                        label = { Text(Tab.Backfill.label, style = MaterialTheme.typography.labelSmall) })
                    Spacer(Modifier.weight(1f))
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "本次 +${lastTokenConsume.renderWithUnit()} t",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "总计 ${totalTokenConsume.renderWithUnit()} t",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                DraggableSplitPane(modifier = Modifier.weight(1f), top = {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        AnimatedContent(targetState = selectedTab, modifier = Modifier.fillMaxSize(), transitionSpec = {
                            val dir = if (targetState > initialState) 1 else -1
                            (slideInHorizontally { w -> dir * w / 4 } + fadeIn()) togetherWith (slideOutHorizontally { w -> -dir * w / 4 } + fadeOut())
                        }, label = "tab-content") { tab ->
                            val contentScroll = rememberScrollState()
                            Column(modifier = Modifier.fillMaxSize().verticalScroll(contentScroll)) {
                                when (tab) {
                                    Tab.Extract -> ExtractPanel(
                                        state = extractState,
                                        onStateChange = { extractState = it },
                                        isRunning = isRunning,
                                        onRun = {
                                            launchOp(prelude = { isRunning = true; logLines.clear() }) {
                                                runExtraction(
                                                    extractState.input,
                                                    extractState.output,
                                                    extractState.mode.key,
                                                    extractState.disableFilter,
                                                    extractState.regionPatternPath,
                                                    extractState.mcfPatternPath,
                                                    extractState.mcjPatternPath
                                                )
                                            }
                                        })

                                    Tab.Translate -> TranslatePanel(
                                        state = translateState,
                                        onStateChange = { translateState = it },
                                        translationProgress = translateProgress,
                                        translationStatus = translateStatus,
                                        isRunning = isRunning,
                                        onRun = {
                                            launchOp(prelude = {
                                                isRunning = true; logLines.clear(); translateProgress =
                                                0f; translateStatus = ""
                                            }) {
                                                either {
                                                    runTranslation(
                                                        input = translateState.input,
                                                        output = translateState.output,
                                                        mappingOutput = translateState.mappingOutput,
                                                        termOutput = translateState.termOutput,
                                                        apiUrl = translateState.apiUrl.ifBlank { null },
                                                        token = translateState.apiToken,
                                                        model = translateState.model,
                                                        termPath = translateState.existingTermPath.ifBlank { null },
                                                        literatureStyle = translateState.literatureStyle,
                                                        onFailure = { scope.launch { snackbarHostState.showSnackbar(it.message) } },
                                                        clientManager = clientManager
                                                    )
                                                }.onLeft { snackbarHostState.showSnackbar(it.message) }
                                            }
                                        },
                                        onSaveSettings = {
                                            logLines.add(
                                                LogEntry(
                                                    null, if (saveSettings(
                                                            translateState.apiUrl,
                                                            translateState.model,
                                                            translateState.apiToken
                                                        )
                                                    ) "API 设置已保存到 $settingsPathString" else "保存 API 设置失败"
                                                )
                                            )
                                        },
                                        onOptimizePrompt = { current ->
                                            val cl = clientManager.chatCompletionCall
                                            if (cl == null) {
                                                logLines.add(LogEntry(LoggerLevel.Error, "请先在 API 设置中连接"))
                                                null
                                            } else {
                                                logLines.add(LogEntry(null, "正在优化翻译风格提示词..."))
                                                either {
                                                    cl.optimizePrompt(current)
                                                }.getOrElse {
                                                    env.logger.error { "优化失败: ${it.message}" }
                                                    snackbarHostState.showSnackbar("优化失败: ${it.message}")
                                                    null
                                                }
                                            }
                                        })

                                    Tab.Backfill -> BackfillPanel(
                                        state = backfillState,
                                        onStateChange = { backfillState = it },
                                        isRunning = isRunning,
                                        onRun = {
                                            launchOp(prelude = { isRunning = true; logLines.clear() }) {
                                                runBackfill(
                                                    env,
                                                    backfillState.input,
                                                    backfillState.replacements,
                                                    backfillState.mode.key
                                                )
                                            }
                                        })
                                }
                            }
                        }
                    }
                }, bottom = {
                    Column(modifier = Modifier.fillMaxSize()) {
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
                                    expanded = showLogSettings, onDismissRequest = { showLogSettings = false }) {
                                    listOf(
                                        LoggerLevel.Info, LoggerLevel.Warning, LoggerLevel.Error, LoggerLevel.Debug
                                    ).forEach { level ->
                                        val checked = level in logLevelFilter
                                        DropdownMenuItem(text = { Text(level.name) }, onClick = {
                                            logLevelFilter =
                                                if (checked) logLevelFilter - level else logLevelFilter + level
                                        }, leadingIcon = {
                                            if (checked) Icon(
                                                Icons.Outlined.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        })
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        val logScroll = rememberScrollState()
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            tonalElevation = 2.dp
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                SelectionContainer {
                                    Text(
                                        text = coloredLogAnnotatedString(logLines.filter { it.level == null || it.level in logLevelFilter }),
                                        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp)
                                            .verticalScroll(logScroll),
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                                    )
                                }
                                TextButton(
                                    modifier = Modifier.align(Alignment.TopEnd),
                                    onClick = { scope.launch { logScroll.animateScrollTo(logScroll.maxValue) } }) {
                                    Text("↓")
                                }
                            }
                        }
                    }
                })
            }

            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}
