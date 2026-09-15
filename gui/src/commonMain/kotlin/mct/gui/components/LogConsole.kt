package mct.gui.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mct.LoggerLevel
import mct.gui.model.LogEntry
import mct.gui.state.LogConsoleState
import mct.gui.util.findPathLinks

/**
 * Console log panel with filter controls, a find bar and reasoning viewer access.
 *
 * Displays a filtered list of log entries with level-based coloring. Supports filtering by
 * [LoggerLevel], searching the visible entries (Ctrl+F or the magnifier button) and following the
 * newest line while it is not being scrolled.
 *
 * Paths that exist on disk are rendered as links; clicking one reveals the file in the platform
 * file manager through [onOpenPath].
 */
@Composable
fun LogConsole(
    logs: LogConsoleState,
    onShowReasoning: () -> Unit,
    onOpenPath: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibleLogLines = logs.visible
    val colors = rememberConsoleColors()
    val motionScheme = MaterialTheme.motionScheme
    var showLogSettings by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val logListState = rememberLazyListState()
    var followLatest by remember { mutableStateOf(true) }
    var isAutoScrolling by remember { mutableStateOf(false) }

    LaunchedEffect(logListState) {
        snapshotFlow { logListState.isScrollInProgress to logListState.canScrollForward }
            .collect { (isScrolling, canScrollForward) ->
                if (isScrolling && canScrollForward && !isAutoScrolling) {
                    followLatest = false
                } else if (!isScrolling && !canScrollForward && !isAutoScrolling) {
                    followLatest = true
                }
            }
    }

    // Keyed by the newest entry's sequence, not the list size: the ring buffer keeps `size`
    // pinned at its cap, which would freeze follow mode after 5000 lines.
    LaunchedEffect(visibleLogLines.lastOrNull()?.sequence, followLatest) {
        if (followLatest && visibleLogLines.isNotEmpty()) {
            isAutoScrolling = true
            try {
                // Streaming logs arrive in batches; snapping avoids a queue of cancelled animations.
                logListState.scrollToItem(visibleLogLines.lastIndex)
            } finally {
                isAutoScrolling = false
            }
        }
    }

    // Ctrl+F bumps the request counter, so an already open bar is re-focused instead of ignored.
    // The request itself lives inside the bar: the field only exists while the bar is visible, and
    // requesting focus for a field that is not composed yet throws.
    val currentHit = logs.currentHit
    LaunchedEffect(currentHit, logs.searchVisible) {
        val hit = currentHit ?: return@LaunchedEffect
        if (!logs.searchVisible) return@LaunchedEffect
        val index = visibleLogLines.indexOfFirst { it.sequence == hit.sequence }
        if (index < 0) return@LaunchedEffect
        // Only move when the entry is off screen, so stepping through hits in view does not jump.
        if (logListState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
            isAutoScrolling = true
            // Following the newest line would immediately scroll away from the hit.
            followLatest = false
            try {
                logListState.animateScrollToItem(index)
            } finally {
                isAutoScrolling = false
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
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
            IconButton(onClick = { logs.openSearch() }) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = "查找日志 (Ctrl+F)",
                    modifier = Modifier.size(18.dp),
                    tint = if (logs.searchVisible) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onShowReasoning) {
                Icon(
                    Icons.Outlined.Psychology,
                    contentDescription = "推理过程",
                    modifier = Modifier.size(18.dp),
                )
            }
            Box {
                IconButton(onClick = { showLogSettings = true }) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = "日志过滤",
                        modifier = Modifier.size(18.dp),
                        tint = if (logs.levelFilter.size < 4) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                LogFilterMenu(
                    expanded = showLogSettings,
                    onDismissRequest = { showLogSettings = false },
                    logLevelFilter = logs.levelFilter,
                    onFilterChange = { logs.levelFilter = it },
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().weight(1f),
            tonalElevation = 2.dp
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (visibleLogLines.isEmpty()) {
                    Text(
                        text = "暂无日志",
                        modifier = Modifier.fillMaxSize()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        style = LogTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                } else {
                    LazyColumn(
                        state = logListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(
                            items = visibleLogLines,
                            key = { entry -> entry.sequence },
                            contentType = { entry -> entry.level ?: "plain" },
                        ) { entry ->
                            LogRow(
                                entry = entry,
                                colors = colors,
                                hits = logs.hitsFor(entry.sequence),
                                currentHit = currentHit?.takeIf { it.sequence == entry.sequence }?.range,
                                onOpenPath = onOpenPath,
                            )
                        }
                    }
                }

                // The find bar and the "back to newest" button share the top-right corner.
                Column(modifier = Modifier.align(Alignment.TopEnd)) {
                    ConsoleFindBar(
                        logs = logs,
                        modifier = Modifier.padding(top = 4.dp, end = 4.dp),
                    )
                    AnimatedVisibility(
                        visible = visibleLogLines.isNotEmpty() && !followLatest,
                        modifier = Modifier.align(Alignment.End),
                        // Match the motion scheme used by every other surface in the app.
                        enter = fadeIn(animationSpec = motionScheme.defaultEffectsSpec()) +
                            scaleIn(animationSpec = motionScheme.defaultSpatialSpec()),
                        exit = fadeOut(animationSpec = motionScheme.fastEffectsSpec()) +
                            scaleOut(animationSpec = motionScheme.fastSpatialSpec()),
                    ) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    isAutoScrolling = true
                                    try {
                                        logListState.animateScrollToItem(visibleLogLines.lastIndex)
                                        followLatest = true
                                    } finally {
                                        isAutoScrolling = false
                                    }
                                }
                            }
                        ) {
                            Text("↓")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Browser-style find bar: a query field, the focused hit counter and up/down/close buttons.
 *
 * The query lives in [LogConsoleState], so the bar itself holds no state beyond focus.
 */
@Composable
private fun ConsoleFindBar(
    logs: LogConsoleState,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val motionScheme = MaterialTheme.motionScheme
    val hits = logs.searchHits.size
    val current = if (hits == 0) 0 else logs.searchHitIndex + 1
    val focusRequester = remember { FocusRequester() }

    AnimatedVisibility(
        visible = logs.searchVisible,
        modifier = modifier,
        enter = fadeIn(animationSpec = motionScheme.fastEffectsSpec()) +
            scaleIn(animationSpec = motionScheme.fastSpatialSpec(), initialScale = 0.96f),
        exit = fadeOut(animationSpec = motionScheme.fastEffectsSpec()) +
            scaleOut(animationSpec = motionScheme.fastSpatialSpec(), targetScale = 0.96f),
    ) {
        // Inside the animated content, so the field being focused is attached: this only composes
        // once the bar is visible, and every Ctrl+F re-runs it through the bumped request counter.
        LaunchedEffect(logs.searchFocusRequest) { focusRequester.requestFocus() }
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = scheme.surfaceContainerHighest,
            tonalElevation = 3.dp,
            shadowElevation = 4.dp,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 12.dp, end = 2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .onPreviewKeyEvent { event -> onFindBarKey(logs, event) }
                ) {
                    BasicTextField(
                        value = logs.searchQuery,
                        onValueChange = { logs.searchQuery = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = scheme.onSurface),
                        cursorBrush = SolidColor(scheme.primary),
                        modifier = Modifier.width(160.dp).focusRequester(focusRequester),
                        decorationBox = { field ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (logs.searchQuery.isEmpty()) {
                                    Text(
                                        "在日志中查找",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    )
                                }
                                field()
                            }
                        },
                    )
                }
                Text(
                    "$current/$hits",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                IconButton(onClick = { logs.stepHit(-1) }, enabled = hits > 0) {
                    Icon(
                        Icons.Outlined.KeyboardArrowUp,
                        contentDescription = "上一个匹配 (Shift+Enter)",
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(onClick = { logs.stepHit(1) }, enabled = hits > 0) {
                    Icon(
                        Icons.Outlined.KeyboardArrowDown,
                        contentDescription = "下一个匹配 (Enter)",
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(onClick = { logs.closeSearch() }) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "关闭查找 (Esc)",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/** Enter/Shift+Enter step through the hits, Escape closes the bar — as in a browser find bar. */
private fun onFindBarKey(logs: LogConsoleState, event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return when (event.key) {
        Key.Enter, Key.NumPadEnter -> {
            logs.stepHit(if (event.isShiftPressed) -1 else 1)
            true
        }

        Key.Escape -> {
            logs.closeSearch()
            true
        }

        else -> false
    }
}

/**
 * One console line. The annotated text is remembered per entry because a row is
 * recomposed whenever anything above it in the list changes.
 */
@Composable
private fun LogRow(
    entry: LogEntry,
    colors: ConsoleColors,
    hits: List<IntRange>,
    currentHit: IntRange?,
    onOpenPath: (String) -> Unit,
) {
    // Resolving paths stats the filesystem, so it is cached per message; entry text never changes.
    val links = remember(entry.message) { findPathLinks(entry.message) }
    val text = remember(entry, colors, links, hits, currentHit, onOpenPath) {
        consoleLine(entry, colors, links, hits, currentHit, onOpenPath)
    }
    SelectionContainer {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            style = LogTextStyle,
        )
    }
}

private val LogTextStyle: TextStyle
    @Composable get() = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)

@Composable
private fun LogFilterMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    logLevelFilter: Set<LoggerLevel>,
    onFilterChange: (Set<LoggerLevel>) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        listOf(
            LoggerLevel.Info, LoggerLevel.Warning, LoggerLevel.Error, LoggerLevel.Debug
        ).forEach { level ->
            val checked = level in logLevelFilter
            DropdownMenuItem(
                text = { Text(level.name) },
                onClick = {
                    onFilterChange(if (checked) logLevelFilter - level else logLevelFilter + level)
                },
                leadingIcon = {
                    if (checked) Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }
    }
}
