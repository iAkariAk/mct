package mct.gui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3ComponentOverrideApi
import androidx.compose.material3.ShortNavigationBarOverride
import androidx.compose.material3.ShortNavigationBarOverrideScope
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.getValue

/** Width of the blurred edge that hints at the destinations off-screen. */
private val EdgeFadeWidth = 28.dp

/**
 * Lays the bar's destinations out as a horizontally scrolling row, with the selection pill painted
 * underneath them and a fading edge that hints at destinations off-screen.
 *
 * It deliberately does not build the items: the shell passes them in as
 * [ShortNavigationBarOverrideScope.content], because that is the only place that knows them — the
 * suite scaffold's item list is private to the library. The pill is therefore the shell's
 * ([BarSelectionPill]'s geometry), painted by this scope's row because the row is the only thing
 * that knows both the item width and the scroll offset.
 *
 * The scroll cannot go on the bar itself: the bar's equal-weight measure policy lays out against its
 * incoming constraints, and a scroll modifier passes `Constraints.Infinity`, which makes it emit
 * `Size(Int.MAX_VALUE, …)` and throw. So the bar keeps a bounded width and only the row inside it
 * scrolls.
 */
@OptIn(ExperimentalMaterial3ComponentOverrideApi::class)
object ScrollableNavigationBarOverride : ShortNavigationBarOverride {
    @Composable
    override fun ShortNavigationBarOverrideScope.ShortNavigationBar() {
        Surface(
            modifier = modifier,
            color = containerColor,
            contentColor = contentColor,
        ) {
            // The host hands its state down so it can scroll the selected destination into view; the
            // row falls back to its own when used without a host.
            val hostState = LocalBarScrollState.current
            val fallback = rememberScrollState()
            val scrollState: ScrollState = hostState ?: fallback
            val selectedIndex = LocalBarSelectedIndex.current

            // A fade is only honest where the row actually continues; an edge with nothing beyond it
            // would dim a destination for no reason.
            val moreToStart by remember(scrollState) { derivedStateOf { scrollState.value > 0 } }
            val moreToEnd by remember(scrollState) {
                derivedStateOf { scrollState.value < scrollState.maxValue }
            }

            // The pill is animated on the composition side (an `Animatable` cannot live in a draw
            // lambda); the row supplies the draw pass, which already carries the scroll translation.
            val pill = rememberBarPill(selectedIndex = selectedIndex)

            // Centred while every destination fits — a landscape phone or a tablet — and pinned to
            // the start once they do not, which is the side a scrolling row exposes.
            val rowAlignment = if (scrollState.maxValue > 0) Alignment.CenterStart else Alignment.Center

            // The gradients are built once per width and colour rather than per frame inside the draw
            // pass: a `Brush.horizontalGradient` allocates a colour array and a shader, which
            // per-frame would be pure churn for a static gradient. The width is the constant
            // `EdgeFadeWidth`, resolved through the density alone — reading it from the measured size
            // would feed a size listener back into the modifier that produced the size.
            val density = LocalDensity.current
            val fadeWidthPx = with(density) { EdgeFadeWidth.toPx() }
            val fade = remember(fadeWidthPx, containerColor) {
                FadeEdge(fadeWidthPx, containerColor)
            }

            Box(Modifier.fillMaxWidth(), contentAlignment = rowAlignment) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(scrollState)
                        // Painted in the row's own draw pass so the pill and the fade are occluded by
                        // the icons and labels above them, and so the pill's height follows the
                        // row's measured height instead of stretching to the window.
                        .drawWithContent {
                            pill.drawPill(this, size = size)
                            drawContent()
                            if (moreToStart) fade.drawEdge(this, size.height, fromLeft = true)
                            if (moreToEnd) fade.drawEdge(this, size.height, fromLeft = false)
                        },
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                ) { content() }
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
