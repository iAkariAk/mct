package mct.gui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState

private val RainbowAccentColors = listOf(
    Color(0xFFFF5F6D),
    Color(0xFFFFC371),
    Color(0xFF47CF73),
    Color(0xFF38BDF8),
    Color(0xFF8B5CF6),
    Color(0xFFFF5F6D),
)

@Composable
fun FrameWindowScope.WindowTitleBar(
    windowState: WindowState,
    onCloseRequest: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onToggleConsole: () -> Unit = {},
    consoleVisible: Boolean = false,
    rainbowAccent: Boolean = false,
) {
    val isMax = windowState.placement == WindowPlacement.Maximized
    val motionScheme = MaterialTheme.motionScheme
    // The frame styles are restored by `applyNativeWindowFrame` on Windows, so the platform
    // animates this transition; where that is unavailable it is a plain, unanimated switch.
    val toggleMax = {
        windowState.placement = if (isMax) WindowPlacement.Floating else WindowPlacement.Maximized
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
                        .fillMaxHeight(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        "MCT - Minecraft 翻译工具",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        // The window controls are fixed-width and 40dp tall, so a wrapped title
                        // would draw a second line clipped by the bar instead of growing it.
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // The console is chrome rather than a destination, so its toggle belongs with the
                // other window controls instead of in the navigation area.
                WinCtlBtn(onClick = onToggleConsole) {
                    Icon(
                        if (consoleVisible) Icons.Outlined.Terminal else Icons.Outlined.SmartDisplay,
                        contentDescription = if (consoleVisible) "隐藏运行日志" else "显示运行日志",
                        modifier = Modifier.size(16.dp),
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
                        transitionSpec = {
                            val enter = fadeIn(animationSpec = motionScheme.fastEffectsSpec()) +
                                scaleIn(animationSpec = motionScheme.fastSpatialSpec(), initialScale = 0.85f)
                            val exit = fadeOut(animationSpec = motionScheme.fastEffectsSpec()) +
                                scaleOut(animationSpec = motionScheme.fastSpatialSpec(), targetScale = 0.85f)
                            enter togetherWith exit
                        },
                        label = "max-btn"
                    ) { maxd ->
                        if (maxd)
                            Box(
                                Modifier.size(12.dp).border(2.dp, Color.White, RectangleShape).padding(2.dp)
                                    .then(Modifier.fillMaxSize()).background(Color.White, RectangleShape)
                            )
                        else
                            Box(Modifier.size(12.dp).border(2.dp, Color.White, RectangleShape))
                    }
                }
                WinCtlBtn(onClick = onCloseRequest, isClose = true) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭", modifier = Modifier.size(16.dp))
                }
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(2.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Gate the ambient animation on the window being visible: otherwise it keeps
            // a 60fps transition alive while the app is minimized.
            if (rainbowAccent && !windowState.isMinimized) {
                RainbowTitleAccent(Modifier.fillMaxSize())
            } else {
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}

/** A draw-only ambient accent; it never invalidates the application color scheme. */
@Composable
private fun RainbowTitleAccent(modifier: Modifier = Modifier) {
    val phase = rememberInfiniteTransition(label = "title-rainbow-accent").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "title-rainbow-accent-phase",
    )

    // The brush is fixed at the bar's width and only translated each frame: rebuilding a
    // LinearGradient (with its colour array and shader cache) per frame was pure churn.
    var width by remember { mutableFloatStateOf(0f) }
    val brush = remember(width) {
        if (width <= 0f) null
        else Brush.horizontalGradient(
            colors = RainbowAccentColors,
            startX = 0f,
            endX = width,
            tileMode = TileMode.Repeated,
        )
    }

    Canvas(modifier = modifier.onSizeChanged { width = it.width.toFloat() }) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val brush = brush ?: return@Canvas
        val shift = phase.value * size.width
        translate(left = shift - size.width) {
            drawRect(
                brush = brush,
                size = Size(size.width, size.height),
            )
        }
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
        targetValue = when {
            isClose && isHovered -> Color(0xFFE53935)
            isHovered -> Color.White.copy(alpha = 0.1f)
            else -> Color.Transparent
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "winctl-bg",
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
