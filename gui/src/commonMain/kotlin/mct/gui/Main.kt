package mct.gui

import androidx.compose.animation.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import arrow.core.raise.either
import kotlinx.coroutines.delay
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

fun main() {
    startKoin { modules(apiModule) }

    application {
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

            val colorScheme = ThemeState.colorScheme ?: if (isDark) darkColorScheme() else lightColorScheme()
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
                            onOpenSettings = { settingsVisible = !settingsVisible },
                            rainbowAccent = GuiSettings.isRainbowTheme,
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
}

// ── 主框架 ────────────────────────────────────────────────────

@Composable
fun App(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val clientManager = koinInject<ClientManager>()
    val vm = remember { AppViewModel(clientManager) }
    val tabScrollStates = remember { Tab.entries.associateWith { ScrollState(initial = 0) } }
    val pageTravelPx = with(LocalDensity.current) { 24.dp.roundToPx() }

    DisposableEffect(Unit) { onDispose { vm.dispose() } }

    // 1. Load persisted settings on startup
    LaunchedEffect(Unit) { vm.settings.load() }

    // 2. Probe API when URL or token changes
    LaunchedEffect(vm.translation.state.apiUrl, vm.translation.state.apiToken) {
        // Avoid opening a client and listing models for every keystroke.
        delay(500)
        vm.translation.setupApiClient()
    }

    // 3. Re-create ChatCompletionCall when model / options change
    LaunchedEffect(vm.translation.state.model, GuiSettings.useStreamApi, GuiSettings.temperature) {
        vm.translation.setupChatCompletion()
    }

    // 4. Debounced auto-save: 停止编辑 3 秒后写入设置
    LaunchedEffect(Unit) { vm.settings.autoSave() }

    Box(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRailPanel(
                selectedTab = vm.selectedTab,
                onTabSelected = { tab ->
                    if (tab != vm.selectedTab) vm.selectedTab = tab
                },
                totalTokenConsume = vm.translation.totalTokenConsume,
                lastTokenConsume = vm.translation.lastTokenConsume,
                uriHandler = uriHandler,
            )

            DraggableSplitPane(modifier = Modifier.weight(1f), top = {
                val motionScheme = MaterialTheme.motionScheme
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    shape = MaterialTheme.shapes.large,
                ) {
                    AnimatedContent(
                        targetState = vm.selectedTab,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = {
                            val dir = if (targetState > initialState) 1 else -1
                            val enter = slideInHorizontally(
                                animationSpec = motionScheme.defaultSpatialSpec(),
                                initialOffsetX = { _ -> dir * pageTravelPx },
                            ) + fadeIn(animationSpec = motionScheme.defaultEffectsSpec())
                            val exit = slideOutHorizontally(
                                animationSpec = motionScheme.fastSpatialSpec(),
                                targetOffsetX = { _ -> -dir * pageTravelPx },
                            ) + fadeOut(animationSpec = motionScheme.fastEffectsSpec())
                            enter togetherWith exit
                        },
                        label = "tab-content"
                    ) { tab ->
                        val contentScroll = tabScrollStates.getValue(tab)
                        Column(modifier = Modifier.fillMaxSize().verticalScroll(contentScroll)) {
                            when (tab) {
                                Tab.Extract -> ExtractPanel(
                                    state = vm.extractState,
                                    onStateChange = { vm.extractState = it },
                                    isRunning = vm.operations.isRunning,
                                    onRun = {
                                        vm.operations.launch {
                                            with(vm.env) {
                                                runExtraction(
                                                    vm.extractState.input,
                                                    vm.extractState.output,
                                                    vm.extractState.mode.key,
                                                    vm.extractState.patterns,
                                                )
                                            }
                                        }
                                    })

                                Tab.Translate -> TranslatePanel(
                                    state = vm.translation.state,
                                    onStateChange = { vm.translation.state = it },
                                    translationProgress = vm.translation.progress,
                                    translationStatus = vm.translation.status,
                                    isRunning = vm.operations.isRunning,
                                    onRun = {
                                        vm.translation.resetProgress()
                                        vm.operations.launch {
                                            with(vm.env) {
                                                either {
                                                    runTranslation(
                                                        input = vm.translation.state.input,
                                                        output = vm.translation.state.output,
                                                        mappingOutput = vm.translation.state.mappingOutput,
                                                        termOutput = vm.translation.state.termOutput,
                                                        termPath = vm.translation.state.existingTermPath.ifBlank { null },
                                                        cachesPath = vm.translation.state.cachesPath.ifBlank { null },
                                                        literatureStyle = vm.translation.state.literatureStyle,
                                                        targetLanguage = vm.translation.state.targetLanguage,
                                                        handleGradientAggressively = vm.translation.state.handleGradientAggressively,
                                                        mapInfo = vm.translation.state.mapInfo,
                                                        extraPrompts = vm.translation.state.extraPrompts.ifBlank { null },
                                                        engine = vm.translation.state.engine,
                                                        api = vm.translation.state.api,
                                                        onFailure = {
                                                            vm.scope.launch {
                                                                vm.snackbarHostState.showSnackbar(it.message)
                                                            }
                                                        },
                                                        clientManager = vm.translation.clientManager,
                                                        onCancel = { _, salvaged ->
                                                            vm.logs.add(
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
                                    onCancel = { vm.operations.cancel() },
                                    onOptimizePrompt = { current ->
                                        vm.translation.optimizePrompt(current)
                                    })

                                Tab.TermExtract -> TermExtractPanel(
                                    state = vm.termExtractState,
                                    onStateChange = { vm.termExtractState = it },
                                    isRunning = vm.operations.isRunning,
                                    onRun = {
                                        vm.operations.launch {
                                            with(vm.env) {
                                                runTermExtraction(
                                                    clientManager = vm.translation.clientManager,
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
                                    onCancel = { vm.operations.cancel() })

                                Tab.Backfill -> BackfillPanel(
                                    state = vm.backfillState,
                                    onStateChange = { vm.backfillState = it },
                                    isRunning = vm.operations.isRunning,
                                    onRun = {
                                        vm.operations.launch {
                                            runBackfill(
                                                vm.env,
                                                vm.backfillState.input,
                                                vm.backfillState.replacements,
                                                vm.backfillState.mode.key,
                                            )
                                        }
                                    })

                                Tab.Patch -> PatchPanel(
                                    state = vm.patchState,
                                    onStateChange = { vm.patchState = it },
                                    isRunning = vm.operations.isRunning,
                                    onCreate = {
                                        vm.operations.launch {
                                            val state = vm.patchState.create
                                            with(vm.env) {
                                                createPatchFile(
                                                    input = state.input,
                                                    mappingPath = state.mapping,
                                                    output = state.output,
                                                    kind = state.kind,
                                                    format = state.format,
                                                    validation = state.validation,
                                                    patterns = state.patterns,
                                                )
                                            }
                                        }
                                    },
                                    onApply = {
                                        vm.operations.launch {
                                            val state = vm.patchState.apply
                                            with(vm.env) {
                                                applyPatchFile(
                                                    input = state.input,
                                                    patchPath = state.patch,
                                                    format = state.format,
                                                    strategy = state.strategy,
                                                )
                                            }
                                        }
                                    },
                                )

                                Tab.Project -> ProjectPanel(
                                    state = vm.projectState,
                                    onStateChange = { vm.projectState = it },
                                    isRunning = vm.operations.isRunning,
                                    onInit = {
                                        vm.operations.launch {
                                            val state = vm.projectState
                                            val projectRoot = with(vm.env) {
                                                initialiseProject(state.directory, state.name, state.source)
                                            }
                                            vm.projectState = state.copy(directory = projectRoot)
                                        }
                                    },
                                    onUpdate = {
                                        vm.operations.launch {
                                            with(vm.env) { updateProject(vm.projectState.directory) }
                                        }
                                    },
                                    onTerms = {
                                        vm.operations.launch {
                                            with(vm.env) { extractProjectTerms(vm.projectState.directory) }
                                        }
                                    },
                                    onTranslate = {
                                        vm.operations.launch {
                                            with(vm.env) { translateProject(vm.projectState.directory) }
                                        }
                                    },
                                    onBuild = {
                                        vm.operations.launch {
                                            with(vm.env) { buildProject(vm.projectState.directory) }
                                        }
                                    },
                                    onPatch = {
                                        vm.operations.launch {
                                            with(vm.env) { assembleProjectPatch(vm.projectState.directory) }
                                        }
                                    },
                                )

                                Tab.Toolbox -> ToolboxPanel(
                                    state = vm.toolboxState,
                                    onStateChange = { vm.toolboxState = it },
                                    isRunning = vm.operations.isRunning,
                                    onRunOperation = { operation ->
                                        vm.operations.launch {
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
                                                        state.poolInput, state.poolOutput, state.mtlxSource
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
                                                            state.commandPatterns,
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
                    logLines = vm.logs.lines,
                    logLevelFilter = vm.logs.levelFilter,
                    onLogLevelFilterChange = { vm.logs.levelFilter = it },
                    onShowReasoning = { vm.reasoning.visible = true },
                )
            })
        }

        SnackbarHost(hostState = vm.snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        if (vm.reasoning.visible) {
            val activeReasoningIds by remember {
                derivedStateOf { vm.reasoning.active.filterValues { it }.keys.toSet() }
            }
            ReasoningSheet(
                reasoningContents = vm.reasoning.contents,
                activeReasoningIds = activeReasoningIds,
                onClear = { vm.reasoning.clear() },
                onDismiss = { vm.reasoning.visible = false }
            )
        }
    }
}
