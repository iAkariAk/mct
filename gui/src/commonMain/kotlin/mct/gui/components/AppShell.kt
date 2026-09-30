package mct.gui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldValue
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import mct.gui.model.Tab

/** Window height below which the console gets no room of its own. */
private val CompactHeight = 480.dp

/** Width at which a window has room for the side rail instead of a bottom bar. */
private val RailWidth = 600.dp

/** Smallest page the shell keeps when the console is expanded. */
private val MinPageHeight = 200.dp

/** Smallest console that is still usable. */
private val MinConsoleHeight = 96.dp

/** Height a bottom bar takes from the window. */
private val SuiteBarHeight = 80.dp

/**
 * Navigation shape for a window.
 *
 * Height is decided first: a short window gets a bottom bar whatever its width, because a rail needs
 * height it does not have. A narrow window gets the compact bar with its icon over label; a wider
 * one the medium bar with the icon beside the label. Past [RailWidth] the standard rail takes over,
 * which carries all seven destinations within the spec's range.
 */
internal fun navigationSuiteTypeFor(width: Dp, height: Dp): NavigationSuiteType = when {
    height < CompactHeight -> NavigationSuiteType.ShortNavigationBarMedium
    width < RailWidth -> NavigationSuiteType.ShortNavigationBarCompact
    else -> NavigationSuiteType.NavigationRail
}

/**
 * Navigation area that follows the window instead of the platform.
 *
 * Destinations go through the library's navigation suite ([NavigationArea]), so the navigation is the
 * Material 3 Expressive component for the window's size class: a short navigation bar on a
 * phone-shaped window and the standard rail on a desktop one, each with its own indicators, motion
 * and colours.
 *
 * The bar scrolls, because the specification caps a navigation bar at five destinations and this app
 * has seven; the rail takes all of them, since the same spec allows three to seven there. The token
 * readout and the console toggle are chrome rather than destinations, so they sit in the title bar:
 * anything appended to this bar would be off screen on a phone, because the bar scrolls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppShell(
    selectedTab: Tab,
    onTabSelected: (Tab) -> Unit,
    consoleVisible: Boolean,
    tabs: List<Tab> = Tab.entries,
    consolePanel: @Composable (Modifier) -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    val scaffoldState = rememberNavigationSuiteScaffoldState()
    val motionScheme = MaterialTheme.motionScheme

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val suiteType = navigationSuiteTypeFor(maxWidth, maxHeight)
        val isBar = suiteType == NavigationSuiteType.ShortNavigationBarCompact ||
            suiteType == NavigationSuiteType.ShortNavigationBarMedium

        val barHeight = if (isBar) SuiteBarHeight else 0.dp
        val paneSpace = (maxHeight - barHeight).coerceAtLeast(0.dp)
        val showConsole = consoleVisible && paneSpace >= MinPageHeight + MinConsoleHeight

        // Fade the bar with the library's own visibility animation rather than a second one: the
        // scaffold already slides it out for its `Hidden` state (the console taking the whole height
        // is the case that uses it), so tying the alpha to the same driver keeps the two in step.
        val barAlpha by animateFloatAsState(
            targetValue = if (scaffoldState.targetValue == NavigationSuiteScaffoldValue.Hidden) 0f else 1f,
            animationSpec = motionScheme.defaultEffectsSpec(),
            label = "bar-fade",
        )

        NavigationArea(
            suiteType = suiteType,
            tabs = tabs,
            selectedTab = selectedTab,
            onTabSelected = onTabSelected,
            state = scaffoldState,
            barAlpha = barAlpha,
        ) {
            if (showConsole) {
                DraggableSplitPane(
                    modifier = Modifier.fillMaxSize(),
                    initialRatio = 0.68f,
                    minTopHeight = MinPageHeight,
                    minBottomHeight = MinConsoleHeight,
                    top = { content(Modifier.fillMaxSize()) },
                    bottom = { consolePanel(Modifier.fillMaxSize()) },
                )
            } else {
                content(Modifier.fillMaxSize())
            }
        }
    }
}
