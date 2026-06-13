package mct.gui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp

@Composable
fun WaveProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val infiniteTransition = rememberInfiniteTransition()
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing)),
    )

    Canvas(modifier = modifier.clip(RoundedCornerShape(4.dp))) {
        val p = progress().coerceIn(0f, 1f)
        val w = size.width
        val h = size.height
        drawRoundRect(trackColor)
        if (p > 0.005f) {
            val shift = wavePhase * 60f
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(primary, tertiary, primary),
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
    var ratio by remember { mutableFloatStateOf(initialRatio) }
    var totalHeight by remember { mutableIntStateOf(0) }

    Column(modifier = modifier.onSizeChanged { totalHeight = it.height }) {
        Box(modifier = Modifier.weight(ratio.coerceIn(minRatio, maxRatio)).fillMaxWidth()) {
            top()
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .pointerInput(totalHeight) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (totalHeight > 0) {
                            ratio = (ratio + dragAmount / totalHeight).coerceIn(minRatio, maxRatio)
                        }
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

        Spacer(Modifier.height(12.dp))

        Box(modifier = Modifier.weight((1f - ratio).coerceIn(minRatio, maxRatio)).fillMaxWidth()) {
            bottom()
        }
    }
}
