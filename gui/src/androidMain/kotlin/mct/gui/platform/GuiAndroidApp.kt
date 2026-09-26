package mct.gui.platform

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3ComponentOverrideApi
import androidx.compose.material3.LocalShortNavigationBarOverride
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveComponentOverrideApi
import androidx.compose.material3.adaptive.navigationsuite.LocalNavigationSuiteScaffoldOverride
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import mct.gui.App
import mct.gui.AppViewModel
import mct.gui.GuiTheme
import mct.gui.components.RoundedNavigationSuiteOverride
import mct.gui.components.ScrollableNavigationBarOverride
import mct.gui.model.GuiSettings
import mct.gui.services.ClientManager
import org.koin.compose.koinInject
import androidx.compose.ui.platform.LocalUriHandler

/**
 * The Android entry point: the same application shell as the desktop window, hosted by an activity
 * instead of a window, with the Android top bar in place of the window chrome.
 *
 * It lives here rather than in the app module so the app module needs neither the Compose compiler
 * nor a composable of its own — it only calls this from `setContent`.
 */
@OptIn(ExperimentalMaterial3AdaptiveComponentOverrideApi::class, ExperimentalMaterial3ComponentOverrideApi::class)
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
                Box(Modifier.weight(1f)) {
                    // Both overrides are Android-only: the desktop window keeps the library's own
                    // rail shape, and its destinations all fit without scrolling.
                    CompositionLocalProvider(
                        LocalNavigationSuiteScaffoldOverride provides RoundedNavigationSuiteOverride,
                        LocalShortNavigationBarOverride provides ScrollableNavigationBarOverride,
                    ) {
                        App(vm)
                    }
                }
            }
        }
    }
}
