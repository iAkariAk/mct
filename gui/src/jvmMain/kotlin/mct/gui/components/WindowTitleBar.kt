package mct.gui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowState
import kotlinx.coroutines.launch

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
                        .fillMaxHeight(),
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
