package mct.gui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.launch
import mct.gui.components.WindowTitleBar
import mct.gui.model.GuiSettings
import mct.gui.services.ClientManager
import mct.gui.services.apiModule
import mct.gui.window.applyNativeWindowFrame
import org.koin.compose.koinInject
import org.koin.core.context.startKoin
import java.awt.Dimension
import androidx.compose.ui.platform.LocalUriHandler

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
            // Restores a real native frame under Compose's own chrome, so the window manager
            // animates maximize/restore/minimize again. Windows only; a no-op elsewhere.
            DisposableEffect(window) {
                applyNativeWindowFrame(window)
                onDispose { }
            }

            LaunchedEffect(Unit) {
                window.minimumSize = Dimension(400, 300)
            }

            // Maximized the window covers the work area edge to edge, so the corner radius would
            // only carve transparent notches out of the desktop. Floating keeps it.
            val windowShape =
                if (state.placement == WindowPlacement.Maximized) RectangleShape else MaterialTheme.shapes.medium
            val uriHandler = LocalUriHandler.current
            GuiTheme(windowShape) {
                Column(Modifier.fillMaxSize()) {
                    WindowTitleBar(
                        state,
                        onCloseRequest = requestClose,
                        onOpenSettings = { vm.settingsVisible = !vm.settingsVisible },
                        onToggleConsole = { vm.consoleVisible = !vm.consoleVisible },
                        consoleVisible = vm.consoleVisible,
                        rainbowAccent = GuiSettings.isRainbowTheme,
        onOpenProjectPage = { uriHandler.openUri("https://github.com/iAkariAk/mct") },
        totalTokenConsume = { vm.translation.totalTokenConsume },
        lastTokenConsume = { vm.translation.lastTokenConsume },
    )
                    Box(Modifier.weight(1f)) {
                        App(vm)
                    }
                }
            }
        }
    }
}
