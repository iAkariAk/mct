package mct.gui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import mct.gui.model.Tab
import mct.gui.util.renderWithUnit

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
 * Destinations the bottom bar carries, in bar order.
 *
 * The Material navigation bar specification asks for three to five destinations, and this app has
 * seven. The three that are a project's working loop stay on the bar; the rest move into the sheet
 * the "more" entry opens. The rail shows every destination, since the spec allows three to seven
 * there.
 */
private val BarTabs = listOf(Tab.Project, Tab.Patch, Tab.Toolbox)

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

private fun navItemIcon(tab: Tab): ImageVector = when (tab) {
    Tab.Extract -> Icons.Outlined.Search
    Tab.Translate -> Icons.Outlined.Translate
    Tab.TermExtract -> Icons.Outlined.Bookmark
    Tab.Backfill -> Icons.Outlined.Restore
    Tab.Patch -> Icons.Outlined.Difference
    Tab.Project -> Icons.Outlined.Workspaces
    Tab.Toolbox -> Icons.Outlined.Handyman
}

/**
 * Navigation area that follows the window instead of the platform.
 *
 * Destinations go through the library's [NavigationSuiteScaffold], so the navigation is the Material
 * 3 Expressive component for the window's size class: a short navigation bar on a phone-shaped
 * window and the standard rail on a desktop one, each with its own indicators, motion and colours.
 *
 * A bottom bar shows [BarTabs] plus a "more" entry, because the specification caps a navigation bar
 * at five destinations and this app has seven; the rail takes all of them, since the same spec
 * allows three to seven there. The console is chrome rather than a destination, so its toggle sits
 * in the window title bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppShell(
    selectedTab: Tab,
    onTabSelected: (Tab) -> Unit,
    totalTokenConsume: () -> Long,
    lastTokenConsume: () -> Int,
    uriHandler: UriHandler,
    consoleVisible: Boolean,
    tabs: List<Tab> = Tab.entries,
    consolePanel: @Composable (Modifier) -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    val scaffoldState = rememberNavigationSuiteScaffoldState()
    var sheetVisible by remember { mutableStateOf(false) }
    // Read here rather than in the caller: the totals change per completed request, and the lambda
    // keeps that read inside the navigation area.
    val tokens: () -> Pair<Long, Int> = remember(totalTokenConsume, lastTokenConsume) {
        { totalTokenConsume() to lastTokenConsume() }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val suiteType = navigationSuiteTypeFor(maxWidth, maxHeight)
        val isBar = suiteType == NavigationSuiteType.ShortNavigationBarCompact ||
            suiteType == NavigationSuiteType.ShortNavigationBarMedium
        // The rail takes every destination; the bar takes [BarTabs] and moves the remainder into the
        // sheet.
        val barTabs = if (isBar) {
            // In [BarTabs] order, and only tabs this build actually has.
            BarTabs.mapNotNull { barTab -> tabs.firstOrNull { it == barTab } }
        } else {
            tabs
        }
        val overflowTabs = if (isBar) tabs.filterNot { it in barTabs } else emptyList()
        // The overflow entry stays selected while the open tab lives behind it, so the bar always
        // shows where the user is.
        val overflowSelected = overflowTabs.any { it == selectedTab }

        val barHeight = if (isBar) SuiteBarHeight else 0.dp
        val paneSpace = (maxHeight - barHeight).coerceAtLeast(0.dp)
        val showConsole = consoleVisible && paneSpace >= MinPageHeight + MinConsoleHeight

        NavigationSuiteScaffold(
            navigationSuiteItems = {
                barTabs.forEach { tab ->
                    item(
                        selected = tab == selectedTab,
                        onClick = { if (tab != selectedTab) onTabSelected(tab) },
                        icon = { Icon(navItemIcon(tab), contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
                if (overflowTabs.isNotEmpty()) {
                    item(
                        selected = overflowSelected,
                        onClick = { sheetVisible = true },
                        icon = { Icon(Icons.Outlined.MoreHoriz, contentDescription = null) },
                        label = { Text("更多") },
                    )
                }
                val (total, last) = tokens()
                if (total > 0L) {
                    // A readout rather than a destination: it carries no selected state, and the
                    // click only opens the project page.
                    item(
                        selected = false,
                        onClick = { uriHandler.openUri("https://github.com/iAkariAk/mct") },
                        icon = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                        },
                        label = { Text("Token") },
                    )
                }
            },
            layoutType = suiteType,
            state = scaffoldState,
            containerColor = MaterialTheme.colorScheme.surface,
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

        if (sheetVisible) {
            ModalBottomSheet(onDismissRequest = { sheetVisible = false }) {
                // Sheet order follows the bar, so the destinations that stayed on the bar are not
                // repeated here.
                overflowTabs.forEach { tab ->
                    NavigationDrawerItem(
                        label = { Text(tab.label) },
                        selected = tab == selectedTab,
                        onClick = {
                            sheetVisible = false
                            if (tab != selectedTab) onTabSelected(tab)
                        },
                        icon = { Icon(navItemIcon(tab), contentDescription = null) },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
