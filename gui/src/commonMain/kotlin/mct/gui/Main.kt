package mct.gui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import arrow.core.raise.either
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import kotlinx.coroutines.launch
import mct.gui.components.DraggableSplitPane
import mct.gui.components.LogConsole
import mct.gui.components.NavigationRailPanel
import mct.gui.components.WindowTitleBar
import mct.gui.model.*
import mct.gui.pages.*
import mct.gui.services.*
import mct.gui.util.ThemeState
import org.koin.compose.koinInject
import org.koin.core.context.startKoin

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun main() = application {
    startKoin { modules(apiModule) }

    val state = rememberWindowState(size = DpSize(820.dp, 760.dp))
    var settingsVisible by remember { mutableStateOf(false) }

    Window(
        onCloseRequest = ::exitApplication,
        state = state,
        undecorated = true,
        transparent = true,
    ) {
        val isDark = isSystemInDarkTheme()

        LaunchedEffect(isDark) {
            window.minimumSize = java.awt.Dimension(400, 300)
            ThemeState.restoreFromSettings(isDark)
        }

        val colorScheme = if (GuiSettings.isRainbowTheme) {
            val rainbow = rememberInfiniteTransition(label = "rainbow")
            val hue by rainbow.animateFloat(
                initialValue = 0f, targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(8000), RepeatMode.Restart),
                label = "rainbowHue"
            )
            dynamicColorScheme(
                seedColor = Color.hsv(hue, 0.9f, 1f),
                isDark = isDark,
                style = PaletteStyle.Vibrant,
            )
        } else {
            ThemeState.colorScheme ?: if (isDark) darkColorScheme() else lightColorScheme()
        }
        MaterialTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(Modifier.fillMaxSize()) {
                    WindowTitleBar(
                        state,
                        onCloseRequest = ::exitApplication,
                        onOpenSettings = { settingsVisible = !settingsVisible }
                    )
                    Box(Modifier.weight(1f)) {
                        App(Modifier.fillMaxSize())
                        SettingsSheet(
                            visible = settingsVisible,
                            onDismiss = { settingsVisible = false }
                        )
                    }
                }
            }
        }
    }
}

// ── 主框架 ────────────────────────────────────────────────────

@Composable
fun App(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val clientManager = koinInject<ClientManager>()
    val vm = remember { AppViewModel(clientManager) }

    DisposableEffect(Unit) { onDispose { vm.dispose() } }

    // 1. Load persisted settings on startup
    LaunchedEffect(Unit) { vm.loadSettings() }

    // 2. Probe API when URL or token changes
    LaunchedEffect(vm.translateState.apiUrl, vm.translateState.apiToken) {
        vm.setupApiClient()
    }

    // 3. Re-create ChatCompletionCall when model / options change
    LaunchedEffect(vm.translateState.model, GuiSettings.useStreamApi, GuiSettings.temperature) {
        vm.setupChatCompletion()
    }

    Box(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRailPanel(
                selectedTab = vm.selectedTab,
                onTabSelected = { vm.selectedTab = it },
                totalTokenConsume = vm.totalTokenConsume,
                lastTokenConsume = vm.lastTokenConsume,
                uriHandler = uriHandler,
            )

            DraggableSplitPane(modifier = Modifier.weight(1f), top = {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    shape = MaterialTheme.shapes.large,
                ) {
                    AnimatedContent(
                        targetState = vm.selectedTab,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = {
                            val dir = if (targetState > initialState) 1 else -1
                            (slideInHorizontally { w -> dir * w / 4 } + fadeIn()) togetherWith (slideOutHorizontally { w -> -dir * w / 4 } + fadeOut())
                        },
                        label = "tab-content"
                    ) { tab ->
                        val contentScroll = rememberScrollState()
                        Column(modifier = Modifier.fillMaxSize().verticalScroll(contentScroll)) {
                            when (tab) {
                                Tab.Extract -> ExtractPanel(
                                    state = vm.extractState,
                                    onStateChange = { vm.extractState = it },
                                    isRunning = vm.isRunning,
                                    onRun = {
                                        vm.launchOp(prelude = {
                                            vm.isRunning = true
                                            vm.logLines.clear()
                                        }) {
                                            with(vm.env) {
                                                runExtraction(
                                                    vm.extractState.input,
                                                    vm.extractState.output,
                                                    vm.extractState.mode.key,
                                                    vm.extractState.disableFilter,
                                                    vm.extractState.regionPatternPath,
                                                    vm.extractState.commandPatternPath,
                                                    vm.extractState.commandDataPatternPath,
                                                    vm.extractState.mcjPatternPath,
                                                    vm.extractState.commandRegexPatternPath,
                                                )
                                            }
                                        }
                                    })

                                Tab.Translate -> TranslatePanel(
                                    state = vm.translateState,
                                    onStateChange = { vm.translateState = it },
                                    translationProgress = vm.translateProgress,
                                    translationStatus = vm.translateStatus,
                                    isRunning = vm.isRunning,
                                    onRun = {
                                        vm.launchOp(prelude = {
                                            vm.isRunning = true
                                            vm.logLines.clear()
                                            vm.translateProgress = 0f
                                            vm.translateStatus = ""
                                        }) {
                                            with(vm.env) {
                                                either {
                                                    runTranslation(
                                                        input = vm.translateState.input,
                                                        output = vm.translateState.output,
                                                        mappingOutput = vm.translateState.mappingOutput,
                                                        termOutput = vm.translateState.termOutput,
                                                        apiUrl = vm.translateState.apiUrl.ifBlank { null },
                                                        token = vm.translateState.apiToken,
                                                        model = vm.translateState.model,
                                                        termPath = vm.translateState.existingTermPath.ifBlank { null },
                                                        cachesPath = vm.translateState.cachesPath.ifBlank { null },
                                                        literatureStyle = vm.translateState.literatureStyle,
                                                        targetLanguage = vm.translateState.targetLanguage,
                                                        handleGradientAggressively = vm.translateState.handleGradientAggressively,
                                                        mapInfo = vm.translateState.mapInfo,
                                                        extraPrompts = vm.translateState.extraPrompts.ifBlank { null },
                                                        temperature = GuiSettings.temperature,
                                                        onFailure = {
                                                            vm.scope.launch {
                                                                vm.snackbarHostState.showSnackbar(it.message)
                                                            }
                                                        },
                                                        clientManager = vm.clientManager,
                                                        onCancel = { _, salvaged ->
                                                            vm.logLines.add(
                                                                LogEntry(
                                                                    null,
                                                                    "翻译被取消，已保存 ${salvaged.size} 条已翻译文本"
                                                                )
                                                            )
                                                        },
                                                    )
                                                }
                                            }.onLeft { vm.scope.launch { vm.snackbarHostState.showSnackbar(it.message) } }
                                        }
                                    },
                                    onCancel = { vm.cancelJob() },
                                    onSaveSettings = {
                                        val ok = vm.saveSettings()
                                        vm.logLines.add(
                                            LogEntry(
                                                null, if (ok) "API 设置已保存到 ${apiSetting.path}"
                                                else "保存 API 设置失败"
                                            )
                                        )
                                    },
                                    onOptimizePrompt = { current ->
                                        vm.optimizePrompt(current)
                                    })

                                Tab.TermExtract -> TermExtractPanel(
                                    state = vm.termExtractState,
                                    onStateChange = { vm.termExtractState = it },
                                    isRunning = vm.isRunning,
                                    onRun = {
                                        vm.launchOp(prelude = { vm.isRunning = true; vm.logLines.clear() }) {
                                            with(vm.env) {
                                                runTermExtraction(
                                                    clientManager = vm.clientManager,
                                                    input = vm.termExtractState.input,
                                                    output = vm.termExtractState.output,
                                                    termPath = vm.termExtractState.existingTermPath.takeIf { it.isNotBlank() },
                                                    targetLanguage = vm.termExtractState.targetLanguage,
                                                    literatureStyle = vm.termExtractState.literatureStyle,
                                                    mapInfo = vm.termExtractState.mapInfo,
                                                    extraPrompts = vm.termExtractState.extraPrompts.ifBlank { null },
                                                )
                                            }
                                        }
                                    },
                                    onCancel = { vm.cancelJob() })

                                Tab.Backfill -> BackfillPanel(
                                    state = vm.backfillState,
                                    onStateChange = { vm.backfillState = it },
                                    isRunning = vm.isRunning,
                                    onRun = {
                                        vm.launchOp(prelude = { vm.isRunning = true; vm.logLines.clear() }) {
                                            runBackfill(
                                                vm.env,
                                                vm.backfillState.input,
                                                vm.backfillState.replacements,
                                                vm.backfillState.mode.key,
                                            )
                                        }
                                    })

                                Tab.Project -> ProjectPanel(
                                    state = vm.projectState,
                                    onStateChange = { vm.projectState = it },
                                    isRunning = vm.isRunning,
                                    onInit = {
                                        vm.launchOp(prelude = { vm.isRunning = true; vm.logLines.clear() }) {
                                            val state = vm.projectState
                                            val projectRoot = with(vm.env) {
                                                initialiseProject(state.directory, state.name, state.source)
                                            }
                                            vm.projectState = state.copy(directory = projectRoot)
                                        }
                                    },
                                    onUpdate = {
                                        vm.launchOp(prelude = { vm.isRunning = true; vm.logLines.clear() }) {
                                            with(vm.env) { updateProject(vm.projectState.directory) }
                                        }
                                    },
                                    onTerms = {
                                        vm.launchOp(prelude = { vm.isRunning = true; vm.logLines.clear() }) {
                                            with(vm.env) { extractProjectTerms(vm.projectState.directory) }
                                        }
                                    },
                                    onTranslate = {
                                        vm.launchOp(prelude = { vm.isRunning = true; vm.logLines.clear() }) {
                                            with(vm.env) { translateProject(vm.projectState.directory) }
                                        }
                                    },
                                    onBuild = {
                                        vm.launchOp(prelude = { vm.isRunning = true; vm.logLines.clear() }) {
                                            with(vm.env) { buildProject(vm.projectState.directory) }
                                        }
                                    },
                                )

                                Tab.Toolbox -> ToolboxPanel(
                                    state = vm.toolboxState,
                                    onStateChange = { vm.toolboxState = it },
                                    isRunning = vm.isRunning,
                                    onRunOperation = { operation ->
                                        vm.launchOp(prelude = { vm.isRunning = true; vm.logLines.clear() }) {
                                            val state = vm.toolboxState
                                            with(vm.env) {
                                                when (operation) {
                                                    ToolboxOperation.PointerTest -> {
                                                        val result = runPointerTest(
                                                            state.pointerKind.key,
                                                            state.pointerPatternPath.takeIf { it.isNotBlank() },
                                                            state.noBuiltin,
                                                            state.pointerInput,
                                                        )
                                                        vm.toolboxState = state.copy(pointerResult = result.toString())
                                                    }

                                                    ToolboxOperation.ExportSnbt -> runExportSnbt(
                                                        state.exportInput,
                                                        state.exportOutput,
                                                    )
                                                    ToolboxOperation.FlattenPool -> flattenTextPool(
                                                        state.poolInput, state.poolOutput, state.poolKind.key, state.poolSimply
                                                    )
                                                    ToolboxOperation.UnflattenPool -> unflattenTextPool(
                                                        state.poolInput, state.mappingInput, state.poolOutput
                                                    )
                                                    ToolboxOperation.GenerateMtlx -> generateMtlxTemplate(
                                                        state.poolInput, state.poolOutput
                                                    )
                                                    ToolboxOperation.TranslateMtlx -> translateByMtlx(
                                                        state.mtlxInput, state.poolInput, state.poolOutput
                                                    )
                                                    ToolboxOperation.ReplaceAll -> replaceAllExtractions(
                                                        state.poolInput, state.poolOutput, state.replacement
                                                    )
                                                    ToolboxOperation.ExportSchema -> exportPatternSchema(
                                                        when (state.schemaKind) {
                                                            SchemaKind.Command -> PatternSchemaKind.Command
                                                            SchemaKind.DataPointer -> PatternSchemaKind.DataPointer
                                                            SchemaKind.CommandRegex -> PatternSchemaKind.CommandRegex
                                                        },
                                                        state.poolOutput,
                                                    )
                                                    ToolboxOperation.CommandTest -> {
                                                        val matches = testCommandPatterns(
                                                            state.commandInput,
                                                            state.commandPatternPath.takeIf { it.isNotBlank() },
                                                            state.commandDataPatternPath.takeIf { it.isNotBlank() },
                                                            state.commandNoBuiltin,
                                                        )
                                                        vm.toolboxState = state.copy(
                                                            commandResult = matches.joinToString("\n") {
                                                                "[${it.start}, ${it.endExclusive}) ${it.content}"
                                                            }.ifBlank { "未匹配到可提取文本。" },
                                                        )
                                                    }
                                                    ToolboxOperation.DownloadOfficialLanguage -> downloadOfficialLanguages(
                                                        state.officialMinecraftVersion,
                                                        state.officialOutput,
                                                        state.officialConcurrency.toInt(),
                                                    )
                                                    ToolboxOperation.CombineOfficialLanguage -> combineOfficialLanguages(
                                                        state.officialSourceLanguage,
                                                        state.officialTargetLanguage,
                                                        state.poolOutput,
                                                    )
                                                }
                                            }
                                        }
                                    })
                            }
                        }
                    }
                }
            }, bottom = {
                LogConsole(
                    logLines = vm.logLines,
                    logLevelFilter = vm.logLevelFilter,
                    onLogLevelFilterChange = { vm.logLevelFilter = it },
                    onShowReasoning = { vm.showReasoning = true },
                )
            })
        }

        SnackbarHost(hostState = vm.snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        if (vm.showReasoning) {
            ReasoningSheet(
                reasoningContents = vm.reasoningContents,
                activeReasoningIds = vm.reasoningActive.filterValues { it }.keys,
                onClear = {
                    vm.reasoningContents.clear()
                    vm.reasoningActive.clear()
                },
                onDismiss = { vm.showReasoning = false }
            )
        }
    }
}
