package mct.gui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun WaveProgressIndicator(
    progress: () -> Float,
    animated: Boolean,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val gradientColors = remember(primary, tertiary) { listOf(primary, tertiary, primary) }
    val wavePhase = if (animated) {
        rememberInfiniteTransition(label = "translation-progress-wave").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "translation-progress-wave-phase",
        )
    } else {
        null
    }

    Canvas(modifier = modifier.clip(RoundedCornerShape(4.dp))) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val p = progress().coerceIn(0f, 1f)
        val w = size.width
        val h = size.height
        drawRoundRect(trackColor)
        if (p > 0.005f) {
            val shift = (wavePhase?.value ?: 0f) * 60f
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = gradientColors,
                    start = Offset(shift, 0f),
                    end = Offset(shift + w, 0f),
                    tileMode = TileMode.Mirror,
                ),
                size = Size(w * p, h),
            )
        }
    }
}

@Composable
fun DraggableSplitPane(
    modifier: Modifier = Modifier,
    initialRatio: Float = 0.7f,
    minRatio: Float = 0.25f,
    maxRatio: Float = 0.85f,
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
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            ratio.floatValue = (
                                ratio.floatValue + dragAmount / availableHeight.intValue
                                ).coerceIn(minRatio, maxRatio)
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
        val topHeight = (paneHeight * ratio.floatValue)
            .roundToInt()
            .coerceIn(0, paneHeight)
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
