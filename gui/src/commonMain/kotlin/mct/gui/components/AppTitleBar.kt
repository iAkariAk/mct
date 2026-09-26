package mct.gui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
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
import mct.gui.util.renderWithUnit

private val RainbowAccentColors = listOf(
    Color(0xFFFF5F6D),
    Color(0xFFFFC371),
    Color(0xFF47CF73),
    Color(0xFF38BDF8),
    Color(0xFF8B5CF6),
    Color(0xFFFF5F6D),
)

/**
 * The title row's content, shared by the desktop window chrome and the Android top bar: the app
 * icon and name, the project link, the console toggle, the settings button, and then whatever the
 * host adds at the end through [windowControls].
 *
 * The desktop host passes its minimize/maximize/close buttons as [windowControls] and wraps the
 * whole row in a draggable area; Android passes nothing, so its bar carries no window operations
 * and is not draggable.
 *
 * [onOpenProjectPage] belongs here rather than in the navigation area: that bar scrolls, so anything
 * at the end of its row is off screen on a phone. Chrome is always visible.
 */
@Composable
fun AppTitleBar(
    modifier: Modifier = Modifier,
    consoleVisible: Boolean,
    onOpenSettings: () -> Unit = {},
    onToggleConsole: () -> Unit = {},
    onOpenProjectPage: () -> Unit = {},
    totalTokenConsume: () -> Long = { 0L },
    lastTokenConsume: () -> Int = { 0 },
    windowControls: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
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

        // The readout lives here, not in the navigation bar: a phone-sized bar scrolls, so anything
        // appended to the end of its row would be permanently off screen.
        val total = totalTokenConsume()
        if (total > 0L) {
            val last = lastTokenConsume()
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Text(
                    total.renderWithUnit(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (last > 0) {
                    Text(
                        "+${last.renderWithUnit()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    )
                }
            }
        }
        WinCtlBtn(onClick = onOpenProjectPage) {
            Icon(Icons.Outlined.Language, contentDescription = "项目主页", modifier = Modifier.size(16.dp))
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

        windowControls()
    }
}

/**
 * The 2dp strip under the title row: the rainbow accent while [animated], a hairline divider
 * otherwise.
 *
 * [animated] is false while the host cannot draw at 60fps — a minimized window, or a platform
 * without the accent — so the ambient transition does not keep running invisibly.
 */
@Composable
fun AppTitleBarAccent(animated: Boolean) {
    Box(
        modifier = Modifier.fillMaxWidth().height(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (animated) {
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

    // The brush is fixed at the bar's width and only translated each frame, so the LinearGradient —
    // its colour array and its shader — is built once rather than rebuilt per frame.
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

/**
 * A chrome button: a fixed 48dp cell that highlights on hover and, for [isClose], turns red.
 *
 * Public because the desktop window controls reuse it next to the shared title row.
 */
@Composable
fun WinCtlBtn(
    onClick: () -> Unit,
    isClose: Boolean = false,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val bg = if (isClose && isHovered) {
        Color(0xFFE53935)
    } else if (isHovered) {
        Color.White.copy(alpha = 0.1f)
    } else {
        Color.Transparent
    }

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
