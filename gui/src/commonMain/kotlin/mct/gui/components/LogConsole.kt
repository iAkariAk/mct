package mct.gui.components

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mct.LoggerLevel
import mct.gui.model.LogEntry

/**
 * Console log panel with filter controls and reasoning viewer access.
 *
 * Displays a filtered list of log entries with level-based coloring.
 * Supports filtering by [LoggerLevel] and provides a "scroll to bottom" button.
 */
@Composable
fun LogConsole(
    visibleLogLines: List<LogEntry>,
    logLevelFilter: Set<LoggerLevel>,
    onLogLevelFilterChange: (Set<LoggerLevel>) -> Unit,
    onShowReasoning: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                        tint = if (logLevelFilter.size < 4) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                LogFilterMenu(
                    expanded = showLogSettings,
                    onDismissRequest = { showLogSettings = false },
                    logLevelFilter = logLevelFilter,
                    onFilterChange = onLogLevelFilterChange,
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
                            LogRow(entry)
                        }
                    }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = visibleLogLines.isNotEmpty() && !followLatest,
                    modifier = Modifier.align(Alignment.TopEnd),
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

/**
 * One console line. The annotated text is remembered per entry because a row is
 * recomposed whenever anything above it in the list changes.
 */
@Composable
private fun LogRow(entry: LogEntry) {
    val text = remember(entry) { coloredLogAnnotatedString(entry) }
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
