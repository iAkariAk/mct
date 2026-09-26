package mct.gui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Vertical split with a draggable handle between [top] and [bottom].
 *
 * Both panes get a hard pixel height so the top pane's scrollable content never forces the
 * split ratio to change. [minTopHeight] / [minBottomHeight] bound the split in absolute terms: a
 * ratio that leaves the console a sliver of a short window is still "25% of the window", which is
 * what made the log unreadable at small sizes. When the two minimums cannot both be satisfied the
 * bottom pane keeps its height and the top one takes the rest; on a window this short the caller
 * is expected to hide the bottom pane instead.
 */
@Composable
fun DraggableSplitPane(
    modifier: Modifier = Modifier,
    initialRatio: Float = 0.7f,
    minRatio: Float = 0.25f,
    maxRatio: Float = 0.85f,
    minTopHeight: Dp = 0.dp,
    minBottomHeight: Dp = 0.dp,
    top: @Composable () -> Unit,
    bottom: @Composable () -> Unit,
) {
    val ratio = remember { mutableFloatStateOf(initialRatio.coerceIn(minRatio, maxRatio)) }
    val availableHeight = remember { mutableIntStateOf(1) }
    val handleHeight = 10.dp
    val bottomSpacing = 12.dp
    val density = LocalDensity.current
    val handleHeightPx = with(density) { handleHeight.roundToPx() }
    val bottomSpacingPx = with(density) { bottomSpacing.roundToPx() }
    val minTopPx = with(density) { minTopHeight.roundToPx() }
    val minBottomPx = with(density) { minBottomHeight.roundToPx() }

    Layout(
        modifier = modifier.onSizeChanged { size ->
            availableHeight.intValue =
                (size.height - handleHeightPx - bottomSpacingPx).coerceAtLeast(1)
        },
        content = {
            Box(modifier = Modifier.fillMaxSize()) { top() }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(handleHeight)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .pointerInput(minTopPx, minBottomPx) {
                        detectVerticalDragGestures { _, dragAmount ->
                            // Only write when the rounded pane height actually changes:
                            // every write re-measures both panes, and the top pane holds
                            // the whole scrollable page.
                            val pane = availableHeight.intValue
                            if (pane <= 0) return@detectVerticalDragGestures
                            val current = (pane * ratio.floatValue).roundToInt()
                            val next = (current + dragAmount.roundToInt()).coerceIn(0, pane)
                            if (next != current) ratio.floatValue = next.toFloat() / pane
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
            Box(modifier = Modifier.fillMaxSize()) { bottom() }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val paneHeight = (height - handleHeightPx - bottomSpacingPx).coerceAtLeast(0)
        // Each pane is clamped on its own, then the top one gives up whatever the two minimums
        // together exceed: `coerceIn` would throw on a window whose minimums cannot both be met
        // (a short pane plus a tall minimum is a normal phone-height window, not a bug).
        var topHeight = (paneHeight * ratio.floatValue).roundToInt().coerceIn(0, paneHeight)
        if (topHeight < minTopPx) topHeight = minTopPx
        val bottomFloor = (paneHeight - minBottomPx).coerceAtLeast(0)
        if (topHeight > bottomFloor) {
            topHeight = if (paneHeight >= minTopPx + minBottomPx) {
                // Both minimums fit, so honour them and split the surplus by the ratio.
                (paneHeight - minBottomPx).coerceAtLeast(minTopPx)
            } else {
                bottomFloor
            }
        }
        val bottomHeight = paneHeight - topHeight

        fun fixedConstraints(childHeight: Int) = constraints.copy(
            minWidth = width,
            maxWidth = width,
            minHeight = childHeight,
            maxHeight = childHeight,
        )

        val topPlaceable = measurables[0].measure(fixedConstraints(topHeight))
        val handlePlaceable = measurables[1].measure(fixedConstraints(handleHeightPx))
        val bottomPlaceable = measurables[2].measure(fixedConstraints(bottomHeight))

        layout(width, height) {
            topPlaceable.placeRelative(0, 0)
            handlePlaceable.placeRelative(0, topHeight)
            bottomPlaceable.placeRelative(0, topHeight + handleHeightPx + bottomSpacingPx)
        }
    }
}
