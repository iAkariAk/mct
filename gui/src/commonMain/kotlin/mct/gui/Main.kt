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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
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
import mct.gui.util.revealInFileExplorer
import mct.gui.window.applyNativeWindowFrame
import org.koin.compose.koinInject
import org.koin.core.context.startKoin
import java.awt.Dimension

/** Ctrl+F (Cmd+F on macOS) opens the console find bar, as it does in a browser. */
private fun KeyEvent.isFindShortcut(): Boolean =
    type == KeyEventType.KeyDown && key == Key.F && (isCtrlPressed || isMetaPressed)

fun main() {
    startKoin { modules(apiModule) }

    application {
        val clientManager = koinInject<ClientManager>()
        // Hoisted out of App() so the window can route title-level shortcuts (Ctrl+F) to the model.
        val vm = remember { AppViewModel(clientManager) }
        val state = rememberWindowState(size = DpSize(820.dp, 760.dp))
        var settingsVisible by remember { mutableStateOf(false) }
        val exitScope = rememberCoroutineScope()

        // Closing writes the settings that are still inside the auto-save debounce window; without
        // it, anything edited in the last few seconds before closing is silently dropped.
        val requestClose: () -> Unit = remember(vm, exitScope) {
            {
                exitScope.launch {
                    try {
                        vm.settings.flush()
                    } finally {
                        exitApplication()
                    }
                }
            }
        }

        Window(
            onCloseRequest = requestClose,
            state = state,
            undecorated = true,
            transparent = true,
            onPreviewKeyEvent = { event ->
                if (event.isFindShortcut()) {
                    vm.logs.openSearch()
                    true
                } else {
                    false
                }
            },
        ) {
            val isDark = isSystemInDarkTheme()

            // Restores a real native frame under Compose's own chrome, so the window manager
            // animates maximize/restore/minimize again. Windows only; a no-op elsewhere.
            DisposableEffect(window) {
                applyNativeWindowFrame(window)
                onDispose { }
            }

            LaunchedEffect(isDark, GuiSettings.seedColorArgb) {
                window.minimumSize = Dimension(400, 300)
                ThemeState.restoreFromSettings(isDark)
            }

            // The persisted dynamic seed only applies while the user has the dynamic theme on.
            val dynamicScheme = if (GuiSettings.isDynamicThemeEnabled) ThemeState.colorScheme else null
            val colorScheme = dynamicScheme ?: if (isDark) darkColorScheme() else lightColorScheme()
            val appModifier = remember { Modifier.fillMaxSize() }
            // Maximized the window covers the work area edge to edge, so the corner radius would
            // only carve transparent notches out of the desktop. Floating keeps it.
            val windowShape =
                if (state.placement == WindowPlacement.Maximized) RectangleShape else MaterialTheme.shapes.medium
            MaterialTheme(
                colorScheme = colorScheme,
                motionScheme = MotionScheme.expressive(),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize().clip(windowShape),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(Modifier.fillMaxSize()) {
                        WindowTitleBar(
                            state,
                            onCloseRequest = requestClose,
                            onOpenSettings = { settingsVisible = !settingsVisible },
                            rainbowAccent = GuiSettings.isRainbowTheme,
                        )
                        Box(Modifier.weight(1f)) {
                            App(vm, appModifier)
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

// ── Application shell ─────────────────────────────────────────

@Composable
fun App(vm: AppViewModel, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    // The project tab brings its own scrolling panes, so only the other tabs get an outer scroll.
    val tabScrollStates = remember {
        Tab.entries.filterNot { it == Tab.Project }.associateWith { ScrollState(initial = 0) }
    }
    val pageTravelPx = with(LocalDensity.current) { 24.dp.roundToPx() }
    val rootModifier = remember { Modifier.fillMaxSize().padding(16.dp) }

    // Panel state setters. These must have a stable identity: they are handed to every panel
    // and, transitively, captured by each field's callback. A fresh instance per recomposition
    // would defeat lambda memoisation, so one keystroke would re-execute every field in the
    // panel instead of just the edited one.
    val setExtractState: (ExtractState) -> Unit = remember(vm) { { vm.extractState = it } }
    val setTranslateState: (TranslateState) -> Unit = remember(vm) { { vm.translation.state = it } }
    val setTermExtractState: (TermExtractState) -> Unit = remember(vm) { { vm.termExtractState = it } }
    val setBackfillState: (BackfillState) -> Unit = remember(vm) { { vm.backfillState = it } }
    val setPatchState: (PatchState) -> Unit = remember(vm) { { vm.patchState = it } }
    val setToolboxState: (ToolboxState) -> Unit = remember(vm) { { vm.toolboxState = it } }

    DisposableEffect(Unit) { onDispose { vm.dispose() } }

    // 1. Load persisted settings on startup
    LaunchedEffect(Unit) { vm.settings.load() }

    // Reading a field of `state` would subscribe this whole composable to the entire
    // TranslateState, so every keystroke in any translate field would recompose the app
    // shell. Derived values keep the subscription narrow.
    val apiUrl by remember { derivedStateOf { vm.translation.state.apiUrl } }
    val apiToken by remember { derivedStateOf { vm.translation.state.apiToken } }
    val model by remember { derivedStateOf { vm.translation.state.model } }

    // 2. Probe API when URL or token changes
    LaunchedEffect(apiUrl, apiToken) {
        // Avoid opening a client and listing models for every keystroke.
        delay(500)
        vm.translation.setupApiClient()
    }

    // 3. Re-create ChatCompletionCall when model / options change
    LaunchedEffect(model, GuiSettings.useStreamApi, GuiSettings.temperature) {
        vm.translation.setupChatCompletion()
    }

    // 4. Debounced auto-save: write settings 3 seconds after editing stops
    LaunchedEffect(Unit) { vm.settings.autoSave() }

    Box(modifier = modifier.then(rootModifier)) {
        Row(modifier = Modifier.fillMaxSize()) {
            val paneModifier = remember { Modifier.weight(1f) }
            NavigationRailPanel(
                selectedTab = vm.selectedTab,
                onTabSelected = { tab ->
                    if (tab != vm.selectedTab) vm.selectedTab = tab
                },
                totalTokenConsume = { vm.translation.totalTokenConsume },
                lastTokenConsume = { vm.translation.lastTokenConsume },
                uriHandler = uriHandler,
            )

            DraggableSplitPane(modifier = paneModifier, top = {
                val motionScheme = MaterialTheme.motionScheme
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
                    label = "tab-content",
                ) { tab ->
                    if (tab == Tab.Project) {
                        // The project workflow renders its own large cards and scrolling
                        // lists, so it sits outside the shared card and outer scroll column.
                        ProjectPanel(
                            controller = vm.project,
                            isRunning = vm.operations.isRunning,
                        )
                    } else {
                        Card(
                            modifier = Modifier.fillMaxSize(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                            shape = MaterialTheme.shapes.large,
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize()
                                    .verticalScroll(tabScrollStates.getValue(tab)),
                            ) {
                                when (tab) {
                                    Tab.Extract -> ExtractPanel(
                                        state = vm.extractState,
                                        onStateChange = setExtractState,
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
                                        onStateChange = setTranslateState,
                                        translationProgress = { vm.translation.progress },
                                        translationStatus = { vm.translation.status },
                                        isRunning = vm.operations.isRunning,
                                        onRun = {
                                            vm.translation.resetProgress()
                                            vm.reasoning.clear()
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
                                                            staticChecking = vm.translation.state.staticChecking,
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
                                        onOptimizePrompt = { vm.translation.optimizeLiteratureStyle() })

                                    Tab.TermExtract -> TermExtractPanel(
                                        state = vm.termExtractState,
                                        onStateChange = setTermExtractState,
                                        isRunning = vm.operations.isRunning,
                                        onRun = {
                                            vm.reasoning.clear()
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
                                        onStateChange = setBackfillState,
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
                                        onStateChange = setPatchState,
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

                                    Tab.Toolbox -> ToolboxPanel(
                                        state = vm.toolboxState,
                                        onStateChange = setToolboxState,
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
                }
            }, bottom = {
                LogConsole(
                    logs = vm.logs,
                    onShowReasoning = { vm.reasoning.visible = true },
                    onOpenPath = { path ->
                        if (!revealInFileExplorer(path)) {
                            vm.scope.launch {
                                vm.snackbarHostState.showSnackbar("无法在资源管理器中打开: $path")
                            }
                        }
                    },
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
