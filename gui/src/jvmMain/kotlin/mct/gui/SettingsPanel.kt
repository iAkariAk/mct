package mct.gui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun SettingsSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally { it },
        exit = slideOutHorizontally { it },
    ) {
        Box(Modifier.fillMaxSize()) {
            // Scrim
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
            // Side sheet
            Box(
                Modifier.align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(280.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(20.dp)
            ) {
                Column {
                    Text(
                        "设置",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(24.dp))

                    TextSwitch(
                        modifier = Modifier.fillMaxWidth(),
                        checked = GuiSettings.prettyOutput,
                        onCheckedChange = { GuiSettings.prettyOutput = it },
                        text = "JSON 美化输出",
                    )
                    Spacer(Modifier.height(24.dp))

                    TextSwitch(
                        modifier = Modifier.fillMaxWidth(),
                        checked = GuiSettings.useStreamApi,
                        onCheckedChange = { GuiSettings.useStreamApi = it },
                        text = "使用流式API（可解决持续空行的过多重试，但可能导致返回变慢）",
                    )

                    Spacer(Modifier.height(24.dp))

                    Text(
                        "Token 分块阈值",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "每批翻译的最大 token 数（超过此值自动分块）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .7f),
                    )
                    Spacer(Modifier.height(12.dp))

                    val sliderRange = 256f..8192f
                    var sliderValue by remember { mutableFloatStateOf(GuiSettings.tokenThreshold.toFloat().coerceIn(sliderRange)) }

                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        onValueChangeFinished = { GuiSettings.tokenThreshold = sliderValue.toInt() },
                        valueRange = sliderRange,
                        steps = 15,
                    )
                    Text(
                        "${sliderValue.toInt()}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }
        }
    }
}
