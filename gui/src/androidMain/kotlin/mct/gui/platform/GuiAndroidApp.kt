package mct.gui.platform

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalUriHandler
import mct.gui.App
import mct.gui.AppViewModel
import mct.gui.GuiTheme
import mct.gui.model.GuiSettings
import mct.gui.services.ClientManager
import org.koin.compose.koinInject

/**
 * The Android entry point: the same application shell as the desktop window, hosted by an activity
 * instead of a window, with the Android top bar in place of the window chrome.
 *
 * It lives here rather than in the app module so the app module needs neither the Compose compiler
 * nor a composable of its own — it only calls this from `setContent`.
 */
@Composable
fun GuiAndroidApp(modifier: Modifier = Modifier) {
    val clientManager = koinInject<ClientManager>()
    val vm = remember { AppViewModel(clientManager) }
    val project = vm.project

    // The project tab's NavDisplay handles back inside the project; outside it, back would leave the
    // activity with the project still open, so closing the project is what back means here.
    BackHandler(enabled = project.opened != null) { project.close() }

    // A square surface: the window is the whole screen here, so a corner radius would clip the
    // background away from the screen's own rounded corners and leave the area behind the system
    // bars unpainted.
    val uriHandler = LocalUriHandler.current
    GuiTheme(RectangleShape) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(modifier.fillMaxSize()) {
                AndroidTitleBar(
                    consoleVisible = vm.consoleVisible,
                    onOpenSettings = { vm.settingsVisible = !vm.settingsVisible },
                    onToggleConsole = { vm.consoleVisible = !vm.consoleVisible },
                    rainbowAccent = GuiSettings.isRainbowTheme,
                    onOpenProjectPage = { uriHandler.openUri("https://github.com/iAkariAk/mct") },
                    totalTokenConsume = { vm.translation.totalTokenConsume },
                    lastTokenConsume = { vm.translation.lastTokenConsume },
                )
                // The shell is inset for the gesture bar: the floating navigation bar is laid out
                // inside the shell, so this is where it can be kept clear of it.
                Box(Modifier.weight(1f).padding(shellBottomInsetPadding())) {
                    App(vm)
                }
            }
        }
    }
}
