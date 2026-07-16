package mct.gui.pages

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReasoningSheet(
    reasoningContents: Map<Int, String>,
    activeReasoningIds: Set<Int>,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val gridState = rememberLazyGridState()
    val entries = reasoningContents.entries.sortedBy { it.key }.map { it.key to it.value }
    val totalCharacters = entries.sumOf { it.second.length }
    var followLatest by remember { mutableStateOf(true) }
    var isGridAutoScrolling by remember { mutableStateOf(false) }

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.isScrollInProgress to gridState.canScrollForward }
            .collect { (isScrolling, canScrollForward) ->
                if (isScrolling && canScrollForward && !isGridAutoScrolling) {
                    followLatest = false
                } else if (!isScrolling && !canScrollForward && !isGridAutoScrolling) {
                    followLatest = true
                }
            }
    }

    LaunchedEffect(entries.size, totalCharacters, followLatest) {
        if (followLatest && entries.isNotEmpty()) {
            isGridAutoScrolling = true
            try {
                gridState.animateScrollToItem(entries.lastIndex)
            } finally {
                isGridAutoScrolling = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Psychology,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("LLM 推理过程", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${entries.size} 个会话 · ${activeReasoningIds.size} 个正在输出",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = onClear,
                    enabled = entries.isNotEmpty(),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Outlined.DeleteSweep, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("清空")
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭推理过程")
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 260.dp, max = 540.dp)
                    .padding(horizontal = 24.dp)
                    .padding(top = 16.dp, bottom = 28.dp),
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 300.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (entries.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            EmptyReasoningState()
                        }
                    } else {
                        items(
                            items = entries,
                            key = { (id, _) -> id },
                            contentType = { "reasoning-card" },
                        ) { (id, content) ->
                            ReasoningCard(
                                id = id,
                                content = content,
                                active = id in activeReasoningIds,
                                followLatest = followLatest,
                                onPauseFollow = { followLatest = false },
                                onResumeFollow = { followLatest = true },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }

                Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !followLatest && entries.isNotEmpty(),
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut(),
                    ) {
                        FilledTonalButton(
                            onClick = { followLatest = true },
                            modifier = Modifier.heightIn(min = 48.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                        ) {
                            Icon(Icons.Outlined.ArrowDownward, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("回到最新输出")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReasoningCard(
    id: Int,
    content: String,
    active: Boolean,
    followLatest: Boolean,
    onPauseFollow: () -> Unit,
    onResumeFollow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentScrollState = rememberScrollState()
    var isAutoScrolling by remember { mutableStateOf(false) }
    val containerColor by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "reasoning-card-color",
    )

    LaunchedEffect(contentScrollState, followLatest) {
        snapshotFlow { contentScrollState.maxValue }.collectLatest { maxValue ->
            if (followLatest) {
                isAutoScrolling = true
                try {
                    contentScrollState.animateScrollTo(maxValue)
                } finally {
                    isAutoScrolling = false
                }
            }
        }
    }

    LaunchedEffect(contentScrollState) {
        snapshotFlow { contentScrollState.isScrollInProgress to contentScrollState.canScrollForward }
            .collect { (isScrolling, canScrollForward) ->
                if (isScrolling && canScrollForward && !isAutoScrolling) {
                    onPauseFollow()
                } else if (!isScrolling && !canScrollForward && !isAutoScrolling) {
                    onResumeFollow()
                }
            }
    }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .height(252.dp)
            .semantics {
                stateDescription = if (active) "正在推理" else "推理已完成"
            },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (active) 3.dp else 1.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (active) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(38.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "#$id",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (active) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text("思考会话 $id", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (active) "正在追加输出" else "输出已完成",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AnimatedVisibility(visible = active, enter = fadeIn(), exit = fadeOut()) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
            AnimatedVisibility(visible = active) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                SelectionContainer {
                    Text(
                        text = content.ifBlank { "等待模型输出…" },
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(contentScrollState)
                            .padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyReasoningState(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Outlined.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text("暂无推理内容", style = MaterialTheme.typography.titleMedium)
            Text(
                "模型开始输出 reasoning_content 后，会在这里自动跟随显示。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
