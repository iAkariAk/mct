package mct.gui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Inset between the pill and the destination it wraps. */
private val PillInset = 6.dp

/** Width one destination occupies on the bar, so the pill knows each destination's position. */
internal val BarItemWidth: Dp = 84.dp

/**
 * The bar's selection pill: one surface that *slides* between destinations.
 *
 * It has to be one piece of chrome: the pill's position is the state, and it animates from the
 * previously selected destination to the newly selected one. A pill drawn per destination could only
 * cross-fade, since it would not know where the selection came from.
 *
 * The geometry is painted in the row's own draw pass (see `ScrollableNavigationBarOverride`) because
 * the pill's horizontal position depends on the row's scroll offset, which only that node knows;
 * this holder owns the animated index.
 *
 * Not a `@Stable` snapshot object: the values here are read inside a draw lambda, and the two that
 * can change afterwards ([color], and the metrics when the density changes) are ordinary state, so a
 * draw that already ran is invalidated when they do. Writing them as plain fields would leave a
 * theme change undrawn until something else happened to invalidate the pass.
 */
internal class BarPill(
    private val itemWidthPx: Float,
    private val insetPx: Float,
) {
    private val index = Animatable(0f)

    /**
     * Whether the pill is drawn at all.
     *
     * Snapshot state, like [color]: both are read by the draw pass, so a plain field would leave an
     * already-recorded draw intact and the pill would change only when something else invalidated
     * it.
     */
    private var visible by mutableStateOf(false)
    private var color: Color by mutableStateOf(Color.Unspecified)

    /** Set from the composition side; a draw pass cannot resolve a `@Composable` colour token. */
    fun updateColor(value: Color) {
        if (color != value) color = value
    }

    /** Follow [selectedIndex] with a light spring; `null` hides the pill. */
    suspend fun animateTo(selectedIndex: Int?) {
        if (selectedIndex == null) {
            visible = false
            return
        }
        val target = selectedIndex.toFloat()
        if (!visible) {
            // First placement: appear under the selection rather than sliding in from the row's
            // start.
            index.snapTo(target)
            visible = true
            return
        }
        visible = true
        if (index.value != target) {
            index.animateTo(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
    }

    /**
     * Paint the pill inside a row of [size].
     *
     * No scroll offset is applied here: this draw pass runs inside the scrolling node, whose
     * translation is already in effect, so the pill moves with the row for free. Subtracting the
     * offset as well would move it twice.
     */
    fun drawPill(scope: DrawScope, size: Size): Unit = with(scope) {
        if (!visible) return
        val pillWidth = itemWidthPx - insetPx * 2
        val pillHeight = size.height - insetPx * 2
        if (pillWidth <= 0f || pillHeight <= 0f) return
        val radius = pillHeight / 2
        drawRoundRect(
            color = color,
            topLeft = Offset(index.value * itemWidthPx + insetPx, insetPx),
            size = Size(pillWidth, pillHeight),
            cornerRadius = CornerRadius(radius, radius),
        )
    }
}

/**
 * Remember a [BarPill] sized for this bar and follow [selectedIndex].
 *
 * Keyed on the resolved metrics rather than on the [LocalDensity] object: a density change alters
 * both pixel values, so the holder has to be rebuilt whenever either does.
 */
@Composable
internal fun rememberBarPill(selectedIndex: Int?): BarPill {
    val density = LocalDensity.current
    val itemWidthPx = with(density) { BarItemWidth.toPx() }
    val insetPx = with(density) { PillInset.toPx() }
    val pill = remember(itemWidthPx, insetPx) {
        BarPill(itemWidthPx = itemWidthPx, insetPx = insetPx)
    }
    // The token colour is read on the composition side and handed to the holder, which cannot call a
    // composable from its draw pass.
    pill.updateColor(ShortNavigationBarItemDefaults.colors().selectedIndicatorColor)
    LaunchedEffect(selectedIndex) { pill.animateTo(selectedIndex) }
    return pill
}
