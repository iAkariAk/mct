package mct.gui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
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
 * Index of the destination the bar's pill should slide to, or `null` when the selected tab is not on
 * the bar. Published to the platform's bar override, which draws the pill but does not know the tab
 * list.
 */
val LocalBarSelectedIndex = compositionLocalOf<Int?> { null }

/**
 * Destinations the bottom bar shows before it starts scrolling.
 *
 * The rail takes every destination, since the Material navigation rail allows three to seven there.
 * The bar shows four at a time and scrolls to the rest: a navigation bar is specified for three to
 * five destinations, and this app has seven.
 */
/**
 * The bar's item-row scroll state.
 *
 * The bar itself must keep a bounded width (its measure policy lays out at `Int.MAX_VALUE` width
 * otherwise), so the scrolling happens on the row of items *inside* it — which is built by the
 * platform's `ShortNavigationBar` override. This local is how the shell, which knows the selected
 * destination, drives a scroller it does not own.
 */
val LocalBarScrollState = compositionLocalOf<ScrollState?> { null }

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
 * The bar scrolls, because the specification caps a navigation bar at five destinations and this app
 * has seven; the rail takes all of them, since the same spec allows three to seven there. The token readout and the console toggle are
 * chrome rather than destinations, so they sit in the title bar: anything appended to this bar would
 * be off screen on a phone, because the bar scrolls.
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
        // Every destination goes on the bar; the row scrolls, so all of them stay reachable.
        val barTabs = tabs
        // Owned here, consumed by the bar's item row (see [LocalBarScrollState]): the shell knows
        // which destination is selected, the row owns the actual scrolling.
        val barScrollState = rememberScrollState()
        val density = LocalDensity.current

        val barHeight = if (isBar) SuiteBarHeight else 0.dp
        val paneSpace = (maxHeight - barHeight).coerceAtLeast(0.dp)
        val showConsole = consoleVisible && paneSpace >= MinPageHeight + MinConsoleHeight

        // Selecting from anywhere else must still bring the destination on screen, or the bar would
        // show a selection the user cannot see.
        LaunchedEffect(selectedTab, isBar) {
            if (!isBar) return@LaunchedEffect
            val index = barTabs.indexOf(selectedTab)
            if (index < 0) return@LaunchedEffect
            val viewport = barScrollState.viewportSize
            if (viewport <= 0) return@LaunchedEffect
            val itemPx = with(density) { BarItemWidth.roundToPx() }
            val maxScroll = (barTabs.size * itemPx - viewport).coerceAtLeast(0)
            // Centre the selection rather than left-aligning it: the neighbouring destinations stay
            // visible, which is what makes the scroll discoverable.
            val target = (index * itemPx - (viewport - itemPx) / 2).coerceIn(0, maxScroll)
            barScrollState.animateScrollTo(target)
        }

        // Fade the bar with the library's own visibility animation rather than a second one: the
        // scaffold already slides it out for its `Hidden` state (the console taking the whole height
        // is the case that uses it), so tying the alpha to the same driver keeps the two in step.
        val barAlpha by animateFloatAsState(
            targetValue = if (scaffoldState.targetValue == NavigationSuiteScaffoldValue.Hidden) 0f else 1f,
            animationSpec = motionScheme.defaultEffectsSpec(),
            label = "bar-fade",
        )

        val selectedBarIndex = barTabs.indexOf(selectedTab).takeIf { it >= 0 }
        // The library draws its own icon-sized pill inside each item; a transparent indicator is
        // what suppresses it, since ours (behind the whole row) is the one that should be visible.
        val barItemColors = if (isBar) {
            NavigationSuiteDefaults.itemColors(
                navigationBarItemColors = NavigationBarItemDefaults.colors(
                    indicatorColor = Color.Transparent,
                ),
            )
        } else {
            null
        }
        CompositionLocalProvider(
            LocalBarScrollState provides (if (isBar) barScrollState else null),
            LocalBarSelectedIndex provides (if (isBar) selectedBarIndex else null),
        ) {
            NavigationSuiteScaffold(
                navigationSuiteItems = {
                    barTabs.forEach { tab ->
                        item(
                            selected = tab == selectedTab,
                            onClick = { if (tab != selectedTab) onTabSelected(tab) },
                            icon = { Icon(navItemIcon(tab), contentDescription = null) },
                            label = { Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            // The pill is drawn behind the whole row (see BarSelectionPill) so it
                            // can slide between destinations; the library's own indicator is sized
                            // to the icon and can never cover the label, so it is suppressed rather
                            // than left to draw a second, smaller pill inside ours. The rail keeps
                            // its own indicator, which is the right shape for a column.
                            colors = barItemColors,
                            modifier = if (isBar) Modifier.width(BarItemWidth) else Modifier,
                        )
                    }
                },
                layoutType = suiteType,
                state = scaffoldState,
                containerColor = MaterialTheme.colorScheme.surface,
                // Bounded by construction (`fillMaxWidth`): the bar's equal-weight measure policy
                // lays out against its incoming constraints, so an unbounded one — what any scroll
                // modifier passes — makes it emit `Size(Int.MAX_VALUE, …)` and crash. The scrolling
                // therefore happens inside the bar, on the item row.
                modifier = if (isBar) Modifier.fillMaxWidth().alpha(barAlpha) else Modifier,
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
}
