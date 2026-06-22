package mct.gui.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.launch
import mct.gui.components.TextSwitch
import mct.gui.model.GuiSettings
import mct.gui.util.getWallpaperPath
import mct.gui.util.rememberImageThemeState

@Composable
fun SettingsSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    val imageThemeState = rememberImageThemeState()
    val scope = rememberCoroutineScope()

    val imagePicker = rememberFilePickerLauncher(
        type = FileKitType.File(),
        mode = FileKitMode.Single,
    ) { file: PlatformFile? ->
        if (file != null) {
            scope.launch { imageThemeState.loadFromPath(file.absolutePath()) }
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally { it },
        exit = slideOutHorizontally { it },
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
            Box(
                Modifier.align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(300.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
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
                    var sliderValue by remember {
                        mutableFloatStateOf(
                            GuiSettings.tokenThreshold.toFloat().coerceIn(sliderRange)
                        )
                    }

                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        onValueChangeFinished = { GuiSettings.tokenThreshold = sliderValue.toInt() },
                        valueRange = sliderRange,
                        steps = 30,
                    )
                    Text(
                        "${sliderValue.toInt()}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )

                    Spacer(Modifier.height(24.dp))

                    Text(
                        "Temperature (0.0–2.0)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "控制 AI 输出的随机性。较低的值使输出更确定，较高的值更具创造性。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .7f),
                    )
                    Spacer(Modifier.height(12.dp))

                    var sliderTemp by remember {
                        mutableFloatStateOf(
                            (GuiSettings.temperature ?: 1.0).toFloat().coerceIn(0f, 2f)
                        )
                    }

                    Slider(
                        value = sliderTemp,
                        onValueChange = { sliderTemp = it },
                        onValueChangeFinished = { GuiSettings.temperature = sliderTemp.toDouble().coerceIn(0.0, 2.0) },
                        valueRange = 0f..2f,
                        steps = 19,
                    )
                    Text(
                        "%.1f".format(sliderTemp),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    Text(
                        when {
                            sliderTemp < 0.5f -> "更确定性"
                            sliderTemp > 1.5f -> "更具创造性"
                            else -> "平衡"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )

                    Spacer(Modifier.height(24.dp))

                    Text(
                        "并发度 (1–20)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "同时进行的 AI 请求数量。较高的值可加速大量文本的翻译，但可能增加 API 限流风险。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .7f),
                    )
                    Spacer(Modifier.height(12.dp))

                    val concurrencyRange = 1f..20f
                    var sliderConc by remember {
                        mutableFloatStateOf(
                            GuiSettings.concurrency.toFloat().coerceIn(concurrencyRange)
                        )
                    }

                    Slider(
                        value = sliderConc,
                        onValueChange = { sliderConc = it },
                        onValueChangeFinished = { GuiSettings.concurrency = sliderConc.toInt().coerceIn(1, 20) },
                        valueRange = concurrencyRange,
                        steps = 18,
                    )
                    Text(
                        "%d  —  %s".format(
                            sliderConc.toInt(),
                            if (sliderConc.toInt() <= 1) "串行" else "并发"
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "注意：并发 > 1 时术语表仅初始术语生效，各 chunk 互不可见",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error.copy(alpha = .8f),
                    )

                    Spacer(Modifier.height(12.dp))

                    TextSwitch(
                        modifier = Modifier.fillMaxWidth(),
                        checked = GuiSettings.concurrentByKind,
                        onCheckedChange = { GuiSettings.concurrentByKind = it },
                        text = "按类型并发（加速多格式混合翻译）",
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "同时翻译 JSON、SNBT、纯文本，充分利用并发度。要求并发度 > 1。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .7f),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "注意：并发翻译时各类型的术语表相互独立",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error.copy(alpha = .8f),
                    )

                    // ── Dynamic Theme ────────────────────────────────
                    Spacer(Modifier.height(24.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Outlined.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                        }
                        Text(
                            "动态主题",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    Spacer(Modifier.height(12.dp))

                    // Source selection buttons (always visible, top)
                    Button(
                        onClick = { imagePicker.launch() },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        ),
                        enabled = !imageThemeState.isProcessing,
                    ) {
                        if (imageThemeState.isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        } else {
                            Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("从图像选取")
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            val path = getWallpaperPath()
                            if (path != null) {
                                scope.launch { imageThemeState.loadFromPath(path) }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !imageThemeState.isProcessing,
                    ) {
                        if (imageThemeState.isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Outlined.Wallpaper, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("从壁纸获取")
                        }
                    }

                    // Error message (inline)
                    if (imageThemeState.errorMessage != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            imageThemeState.errorMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    // Seed color preview card (only when set)
                    GuiSettings.seedColor?.let { activeColor ->
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Box(
                                        Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(activeColor)
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "当前主题色",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            "#%08X".format(activeColor.toArgb()),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Switch(
                                        checked = GuiSettings.isDynamicThemeEnabled,
                                        onCheckedChange = { GuiSettings.isDynamicThemeEnabled = it },
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                TextButton(
                                    onClick = { imageThemeState.reset() },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("重置为默认主题", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }

                    // ── Rainbow gradient theme ────────────────────────
                    Spacer(Modifier.height(12.dp))
                    TextSwitch(
                        modifier = Modifier.fillMaxWidth(),
                        checked = GuiSettings.isRainbowTheme,
                        onCheckedChange = { GuiSettings.isRainbowTheme = it },
                        text = "🌈 七彩渐变主题",
                    )

                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}
