package mct.gui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    logLines: List<LogEntry>,
    logLevelFilter: Set<LoggerLevel>,
    onLogLevelFilterChange: (Set<LoggerLevel>) -> Unit,
    onShowReasoning: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showLogSettings by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val filteredLogLines by remember {
        derivedStateOf {
            logLines.filter { it.level == null || it.level in logLevelFilter }
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
        val logScroll = rememberScrollState()
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().weight(1f),
            tonalElevation = 2.dp
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (filteredLogLines.isEmpty()) {
                    Text(
                        text = "暂无日志",
                        modifier = Modifier.fillMaxSize()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                } else {
                    SelectionContainer {
                        Text(
                            text = coloredLogAnnotatedString(filteredLogLines),
                            modifier = Modifier.fillMaxSize()
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .verticalScroll(logScroll),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                        )
                    }
                }
                if (filteredLogLines.isNotEmpty()) {
                    TextButton(
                        modifier = Modifier.align(Alignment.TopEnd),
                        onClick = { scope.launch { logScroll.animateScrollTo(logScroll.maxValue) } }
                    ) {
                        Text("↓")
                    }
                }
            }
        }
    }
}

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
