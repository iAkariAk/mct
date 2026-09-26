package mct.gui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState

/**
 * Desktop window chrome: the shared title row inside a draggable area, plus the three window
 * operations the platform offers.
 *
 * Android has no counterpart for the controls and its bar is not draggable, so both stay in
 * `desktopMain` and the Android bar composes [AppTitleBar] directly.
 */
@Composable
fun FrameWindowScope.WindowTitleBar(
    windowState: WindowState,
    onCloseRequest: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onToggleConsole: () -> Unit = {},
    consoleVisible: Boolean = false,
    rainbowAccent: Boolean = false,
    onOpenProjectPage: () -> Unit = {},
    totalTokenConsume: () -> Long = { 0L },
    lastTokenConsume: () -> Int = { 0 },
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
            AppTitleBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(start = 12.dp),
                consoleVisible = consoleVisible,
                onOpenSettings = onOpenSettings,
                onToggleConsole = onToggleConsole,
                onOpenProjectPage = onOpenProjectPage,
                totalTokenConsume = totalTokenConsume,
                lastTokenConsume = lastTokenConsume,
            ) {
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
        // Gate the ambient animation on the window being visible: otherwise it keeps
        // a 60fps transition alive while the app is minimized.
        AppTitleBarAccent(rainbowAccent && !windowState.isMinimized)
    }
}
