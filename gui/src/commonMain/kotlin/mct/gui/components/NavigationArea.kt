package mct.gui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldLayout
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldState
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import mct.gui.model.Tab

/** Width of the fading edge that hints at the destinations off-screen. */
private val EdgeFadeWidth = 28.dp

/** Corner radius of the floating bottom bar. */
private val BarShape = RoundedCornerShape(24.dp)

/** Gap between the rounded bar and the window edges. */
private val BarMargin = 12.dp

/**
 * Insets for the bar's own layout.
 *
 * Zero, not the library's system-bar default: the bar is a rounded surface floating [BarMargin] clear
 * of the window edge, so padding it for the system bars again would lift its items off a surface that
 * already stops short of them.
 */
private val NoBarInsets = WindowInsets(0, 0, 0, 0)

/** Destination icons, in [Tab] order their own case handles. */
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
 * The navigation area: the destinations in the Material component the window has room for, and the
 * page beside or above them.
 *
 * The placement comes from [NavigationSuiteScaffoldLayout] — the composition-side form of the
 * scaffold, which takes the navigation component as a slot. Building the bar ourselves is what the
 * old `LocalShortNavigationBarOverride` used to do, and it is still what this app needs: the bar
 * scrolls, because the specification caps a bar at five destinations and there are seven, and only a
 * bar this app lays out itself can be centred on the selected destination. A rail takes all seven,
 * which is inside the spec's three-to-seven range, so it is the library's own.
 *
 * A rail keeps the library's shape. A bar does not: it is a rounded surface floating clear of the
 * window edge, which the library has no parameter for.
 */
@Composable
internal fun NavigationArea(
    suiteType: NavigationSuiteType,
    tabs: List<Tab>,
    selectedTab: Tab,
    onTabSelected: (Tab) -> Unit,
    state: NavigationSuiteScaffoldState,
    barAlpha: Float,
    content: @Composable () -> Unit,
) {
    val isBar = suiteType == NavigationSuiteType.ShortNavigationBarCompact ||
        suiteType == NavigationSuiteType.ShortNavigationBarMedium

    NavigationSuiteScaffoldLayout(
        navigationSuiteType = suiteType,
        state = state,
        navigationSuite = {
            if (isBar) {
                ScaffoldingNavigationBar(
                    tabs = tabs,
                    selectedTab = selectedTab,
                    onTabSelected = onTabSelected,
                    // The compact bar stacks its icon over the label, the medium one puts the icon
                    // beside it; that is the same split the library makes for these two types.
                    iconPosition = if (suiteType == NavigationSuiteType.ShortNavigationBarMedium) {
                        NavigationItemIconPosition.Start
                    } else {
                        NavigationItemIconPosition.Top
                    },
                    modifier = Modifier.fillMaxWidth().padding(BarMargin).clip(BarShape),
                )
            } else {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.alpha(barAlpha),
                ) {
                    tabs.forEach { tab ->
                        NavigationRailItem(
                            selected = tab == selectedTab,
                            onClick = { if (tab != selectedTab) onTabSelected(tab) },
                            icon = { Icon(navItemIcon(tab), contentDescription = null) },
                            label = {
                                Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            colors = NavigationRailItemDefaults.colors(
                                indicatorColor = Color.Transparent,
                            ),
                        )
                    }
                }
            }
        },
        content = content,
    )
}

/**
 * The bar's destinations as a horizontally scrolling row.
 *
 * It has to be built here rather than by [NavigationSuiteScaffoldLayout]: the bar's equal-weight
 * measure policy lays out against its incoming constraints, so a scroll modifier on the bar itself —
 * which passes `Constraints.Infinity` — makes it emit `Size(Int.MAX_VALUE, …)` and throw. The bar
 * therefore keeps a bounded width and only the row inside it scrolls.
 *
 * The selection pill is one shared surface behind the whole row ([BarSelectionPill]) rather than the
 * library's per-item indicator: a pill anchored to an item can only cross-fade, whereas a shared one
 * springs from the old destination to the new, and it can cover the label, which the library's
 * icon-sized indicator cannot. The library's own is suppressed with a transparent indicator colour
 * so the two do not both draw.
 */
@Composable
private fun ScaffoldingNavigationBar(
    tabs: List<Tab>,
    selectedTab: Tab,
    onTabSelected: (Tab) -> Unit,
    iconPosition: NavigationItemIconPosition,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val selectedIndex = tabs.indexOf(selectedTab).takeIf { it >= 0 }
    val containerColor = MaterialTheme.colorScheme.surface

    // Selecting from anywhere else must still bring the destination on screen, or the bar would show
    // a selection the user cannot see.
    LaunchedEffect(selectedTab, tabs) {
        if (selectedIndex == null) return@LaunchedEffect
        val viewport = scrollState.viewportSize
        if (viewport <= 0) return@LaunchedEffect
        val itemPx = with(density) { BarItemWidth.roundToPx() }
        val maxScroll = (tabs.size * itemPx - viewport).coerceAtLeast(0)
        // Centre the selection rather than left-aligning it: the neighbouring destinations stay
        // visible, which is what makes the scroll discoverable.
        val target = (selectedIndex * itemPx - (viewport - itemPx) / 2).coerceIn(0, maxScroll)
        scrollState.animateScrollTo(target)
    }

    // A fade is only honest where the row actually continues; an edge with nothing beyond it would
    // dim a destination for no reason.
    val moreToStart by remember(scrollState) { derivedStateOf { scrollState.value > 0 } }
    val moreToEnd by remember(scrollState) {
        derivedStateOf { scrollState.value < scrollState.maxValue }
    }

    // The pill is animated on the composition side (an `Animatable` cannot live in a draw lambda);
    // the row supplies the draw pass, which already carries the scroll translation.
    val pill = rememberBarPill(selectedIndex)

    // Centred while every destination fits — a landscape phone or a tablet — and pinned to the start
    // once they do not, which is the side a scrolling row exposes.
    val rowAlignment = if (scrollState.maxValue > 0) Alignment.CenterStart else Alignment.Center

    // The gradients are built once per width and colour rather than per frame inside the draw pass:
    // a `Brush.horizontalGradient` allocates a colour array and a shader, which per-frame would be
    // pure churn for a static gradient. The width is the constant `EdgeFadeWidth`, resolved through
    // the density alone — reading it from the measured size would feed a size listener back into the
    // modifier that produced the size.
    val fadeWidthPx = with(density) { EdgeFadeWidth.toPx() }
    val fade = remember(fadeWidthPx, containerColor) { FadeEdge(fadeWidthPx, containerColor) }

    Surface(modifier = modifier, color = containerColor) {
        Box(Modifier.fillMaxWidth(), contentAlignment = rowAlignment) {
            Row(
                modifier = Modifier
                    .horizontalScroll(scrollState)
                    // Painted in the row's own draw pass so the pill and the fade are occluded by the
                    // icons and labels above them, and so the pill's height follows the row's
                    // measured height instead of stretching to the window.
                    .drawWithContent {
                        pill.drawPill(this, size = size)
                        drawContent()
                        if (moreToStart) fade.drawEdge(this, size.height, fromLeft = true)
                        if (moreToEnd) fade.drawEdge(this, size.height, fromLeft = false)
                    }
                    .defaultMinSize(minHeight = BarHeight),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEach { tab ->
                    ShortNavigationBarItem(
                        selected = tab == selectedTab,
                        onClick = { if (tab != selectedTab) onTabSelected(tab) },
                        icon = { Icon(navItemIcon(tab), contentDescription = null) },
                        label = { Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        // A transparent indicator suppresses the library's own pill, which is sized
                        // to the icon and can never cover the label; ours sits behind the whole row.
                        colors = ShortNavigationBarItemDefaults.colors(
                            selectedIndicatorColor = Color.Transparent,
                        ),
                        iconPosition = iconPosition,
                        modifier = Modifier.width(BarItemWidth),
                    )
                }
            }
        }
    }
}

/**
 * The gradients that dissolve a row's edges into [color].
 *
 * Built once per width and colour rather than per frame: a `Brush.horizontalGradient` allocates its
 * colour array and its shader, and both the edge width and the colour are constant for every frame
 * that shares them, so rebuilding them inside the draw pass would be pure churn.
 *
 * The two directions need opposite ramps — opaque at the outer edge, transparent inwards — so they
 * are two brushes, each used by exactly one edge.
 */
private class FadeEdge(width: Float, color: Color) {
    private val opaque = color
    private val clear = color.copy(alpha = 0f)
    private val widthPx = width

    private val startBrush: Brush? = if (width <= 0f) null else Brush.horizontalGradient(
        colors = listOf(opaque, clear),
        startX = 0f,
        endX = width,
    )
    private val endBrush: Brush? = if (width <= 0f) null else Brush.horizontalGradient(
        colors = listOf(clear, opaque),
        startX = 0f,
        endX = width,
    )

    fun drawEdge(scope: DrawScope, height: Float, fromLeft: Boolean): Unit = with(scope) {
        val brush = (if (fromLeft) startBrush else endBrush) ?: return@with
        if (widthPx <= 0f || height <= 0f) return@with
        val left = if (fromLeft) 0f else size.width - widthPx
        drawRect(brush = brush, topLeft = Offset(left, 0f), size = Size(widthPx, height))
    }
}

/**
 * Height of the scrolling row inside the bar.
 *
 * The library's bar is 64dp tall; the row is given the app's taller bar so the floating surface has
 * the same weight as the one the previous platform override drew.
 */
private val BarHeight: Dp = 64.dp
