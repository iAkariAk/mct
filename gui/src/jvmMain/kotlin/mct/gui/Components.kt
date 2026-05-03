package mct.gui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowState
import kotlinx.coroutines.launch
import mct.LoggerLevel
import mct.extra.ai.translator.CustomizedPrompts

// ── 可复用组件 ───────────────────────────────────────────────

@Composable
fun SectionTitle(text: String, icon: ImageVector? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (icon != null) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 1.dp,
                modifier = Modifier.size(28.dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun PathRow(
    label: String, placeholder: String,
    value: String, onValueChange: (String) -> Unit,
    onBrowse: () -> Unit
) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text(placeholder) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
            FilledTonalButton(
                onClick = onBrowse,
                shape = RoundedCornerShape(10.dp),
            ) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("浏览")
            }
        }
    }
}

@Composable
fun ModeRadio(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun ActionButton(label: String, running: Boolean, onClick: () -> Unit, enabled: Boolean) {
    Button(
        onClick = onClick,
        enabled = enabled && !running,
        modifier = Modifier.fillMaxWidth().height(44.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        if (running) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(Modifier.width(8.dp))
            Text("运行中...")
        } else {
            Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(label)
        }
    }
}

// ── TextSwitch ─────────────────────────────────────────────────

@Composable
fun TextSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(Modifier.width(16.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ── Enums ──────────────────────────────────────────────────────

enum class Tab(val label: String) {
    Extract("提取文本"),
    Translate("AI 翻译"),
    Backfill("回填存档")
}

enum class RunMode(val key: String, val label: String) {
    Region("region", "Region (.mca 区域文件)"),
    Datapack("datapack", "Datapack (数据包)")
}

// ── State holders ──────────────────────────────────────────────

data class ExtractState(
    val input: String = "",
    val output: String = "extractions.json",
    val mode: RunMode = RunMode.Region,
    val disableFilter: Boolean = false,
    val regionPatternPath: String = "",
    val mcfPatternPath: String = "",
    val mcjPatternPath: String = "",
)

data class TranslateState(
    val input: String = "extractions.json",
    val output: String = "replacements.json",
    val mappingOutput: String = "mappings.json",
    val termOutput: String = "terms.json",
    val apiUrl: String = "",
    val apiToken: String = "",
    val model: String = "gpt-4o",
    val availableModels: List<String> = emptyList(),
    val isModelsLoading: Boolean = false,
    val useStreamApi: Boolean = false,
    val existingTermPath: String = "",
    val literatureStyle: String = CustomizedPrompts.literatureStyle,
    val isOptimizing: Boolean = false,
)

data class BackfillState(
    val input: String = "",
    val replacements: String = "replacements.json",
    val mode: RunMode = RunMode.Region,
)

// ── 通用组件 ─────────────────────────────────────────────────

@Composable
fun ConfigTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    placeholder: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    modifier: Modifier = Modifier.fillMaxWidth(),
    visualTransformation: VisualTransformation = VisualTransformation.None,
    readOnly: Boolean = false,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = placeholder,
        singleLine = singleLine,
        modifier = modifier,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        readOnly = readOnly,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
        )
    )
}

// ── 结构化日志 ────────────────────────────────────────────────

data class LogEntry(
    val level: LoggerLevel?,
    val message: String,
)

fun coloredLogAnnotatedString(logLines: List<LogEntry>) = buildAnnotatedString {
    for ((i, entry) in logLines.withIndex()) {
        if (i > 0) append("\n")
        val level = entry.level ?: run {
            withStyle(SpanStyle(color = Color(0xFFE0E0E0))) { append(entry.message) }
            return@buildAnnotatedString
        }
        val badgeColor = when (level) {
            LoggerLevel.Info -> Color(0xFF4CAF50)
            LoggerLevel.Warning -> Color(0xFFFFA726)
            LoggerLevel.Error -> Color(0xFFEF5350)
            LoggerLevel.Debug -> Color(0xFF78909C)
            LoggerLevel.Sign -> Color(0xFF9C27B0)
        }
        val badgeLetter = level.name.first().uppercase()
        val textColor = when (level) {
            LoggerLevel.Info -> Color(0xFFC8E6C9)
            LoggerLevel.Warning -> Color(0xFFFFF3E0)
            LoggerLevel.Error -> Color(0xFFFFCDD2)
            LoggerLevel.Debug -> Color(0xFFB0BEC5)
            LoggerLevel.Sign -> Color(0xFFE1BEE7)
        }
        withStyle(SpanStyle(
            color = Color.White, background = badgeColor,
            fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
        )) { append(" $badgeLetter ") }
        append(" ")
        withStyle(SpanStyle(color = textColor)) { append(entry.message) }
    }
}

// ── 波浪进度条 ────────────────────────────────────────────────

@Composable
fun WaveProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val infiniteTransition = rememberInfiniteTransition()
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing)),
    )

    Canvas(modifier = modifier.clip(RoundedCornerShape(4.dp))) {
        val p = progress().coerceIn(0f, 1f)
        val w = size.width
        val h = size.height
        drawRoundRect(trackColor)
        if (p > 0.005f) {
            val shift = wavePhase * 60f
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(primary, tertiary, primary),
                    start = Offset(shift, 0f),
                    end = Offset(shift + w, 0f),
                    tileMode = TileMode.Mirror,
                ),
                size = Size(w * p, h),
            )
        }
    }
}

// ── 可拖拽分割面板 ────────────────────────────────────────────

@Composable
fun DraggableSplitPane(
    modifier: Modifier = Modifier,
    initialRatio: Float = 0.7f,
    minRatio: Float = 0.25f,
    maxRatio: Float = 0.85f,
    top: @Composable () -> Unit,
    bottom: @Composable () -> Unit,
) {
    var ratio by remember { mutableFloatStateOf(initialRatio) }
    var totalHeight by remember { mutableIntStateOf(0) }

    Column(modifier = modifier.onSizeChanged { totalHeight = it.height }) {
        Box(modifier = Modifier.weight(ratio.coerceIn(minRatio, maxRatio)).fillMaxWidth()) {
            top()
        }

        // 拖拽手柄
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .pointerInput(totalHeight) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (totalHeight > 0) {
                            ratio = (ratio + dragAmount / totalHeight).coerceIn(minRatio, maxRatio)
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }

        Spacer(Modifier.height(12.dp))

        Box(modifier = Modifier.weight((1f - ratio).coerceIn(minRatio, maxRatio)).fillMaxWidth()) {
            bottom()
        }
    }
}


@Composable
fun FrameWindowScope.WindowTitleBar(
    windowState: WindowState,
    onCloseRequest: () -> Unit,
    onOpenSettings: () -> Unit = {},
) {
    var isMax by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val anim = remember { Animatable(0f) }
    val savedBounds = remember { mutableStateOf<java.awt.Rectangle?>(null) }

    val toggleMax = {
        isMax = !isMax
        scope.launch {
            val screen = window.graphicsConfiguration.bounds
            val from = window.bounds
            val to = if (isMax) { savedBounds.value = from; screen }
                     else savedBounds.value ?: java.awt.Rectangle(100, 100, 820, 760)
            anim.snapTo(0f)
            anim.animateTo(1f, tween(130)) {
                val v = value
                window.setBounds(
                    (from.x + (to.x - from.x) * v).toInt(),
                    (from.y + (to.y - from.y) * v).toInt(),
                    (from.width + (to.width - from.width) * v).toInt(),
                    (from.height + (to.height - from.height) * v).toInt()
                )
            }
            window.setBounds(to.x, to.y, to.width, to.height)
        }
    }

    Column(Modifier.fillMaxWidth()) {
        WindowDraggableArea(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Translate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = { toggleMax() })
                        },
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        "MCT - Minecraft 翻译工具",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                WinCtlBtn(onClick = onOpenSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = "设置", modifier = Modifier.size(16.dp))
                }

                WinCtlBtn(onClick = { windowState.isMinimized = true }) {
                    Box(Modifier.size(14.dp, 2.dp).background(Color.White, RectangleShape))
                }
                WinCtlBtn(onClick = { toggleMax() }) {
                    AnimatedContent(
                        targetState = isMax,
                        label = "max-btn"
                    ) { maxd ->
                        if (maxd)
                            Box(Modifier.size(12.dp).border(2.dp, Color.White, RectangleShape).padding(2.dp).then(Modifier.fillMaxSize()).background(Color.White, RectangleShape))
                        else
                            Box(Modifier.size(12.dp).border(2.dp, Color.White, RectangleShape))
                    }
                }
                WinCtlBtn(onClick = onCloseRequest, isClose = true) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭", modifier = Modifier.size(16.dp))
                }
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
fun WinCtlBtn(
    onClick: () -> Unit,
    isClose: Boolean = false,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val bg by animateColorAsState(
        when {
            isClose && isHovered -> Color(0xFFE53935)
            isHovered -> Color.White.copy(alpha = 0.1f)
            else -> Color.Transparent
        },
        label = "winctl-bg"
    )

    Box(
        modifier = Modifier
            .width(48.dp)
            .fillMaxHeight()
            .hoverable(interactionSource)
            .clickable(onClick = onClick)
            .background(bg, RectangleShape),
        contentAlignment = Alignment.Center
    ) { content() }
}