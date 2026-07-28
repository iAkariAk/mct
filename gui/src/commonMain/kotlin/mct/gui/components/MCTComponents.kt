package mct.gui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mct.extra.ai.translator.MapInfo

private enum class ActionButtonVisualState {
    Idle,
    Running,
    Cancellable,
}

@Composable
fun SectionTitle(text: String, icon: ImageVector? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (icon != null) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 1.dp,
                modifier = Modifier.size(28.dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun PathRow(
    label: String, placeholder: String,
    value: String, onValueChange: (String) -> Unit,
    onBrowse: () -> Unit,
) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text(placeholder) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
            FilledTonalButton(
                onClick = onBrowse,
                shape = RoundedCornerShape(10.dp),
            ) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("浏览")
            }
        }
    }
}

@Composable
fun ModeRadio(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun ActionButton(
    label: String,
    running: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val hasCancel = onCancel != null
    val density = LocalDensity.current
    val motionScheme = MaterialTheme.motionScheme
    val visualState = when {
        running && hasCancel -> ActionButtonVisualState.Cancellable
        running -> ActionButtonVisualState.Running
        else -> ActionButtonVisualState.Idle
    }
    val buttonEnabled = if (visualState == ActionButtonVisualState.Cancellable) {
        true
    } else {
        enabled && visualState == ActionButtonVisualState.Idle
    }

    // MD3 expressive: animate container color smoothly between primary and error
    val containerColor by animateColorAsState(
        targetValue = if (visualState == ActionButtonVisualState.Cancellable) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primary
        },
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "cancelBtnContainerColor"
    )

    // MD3 expressive: animate content color to match container
    val contentColor by animateColorAsState(
        targetValue = if (visualState == ActionButtonVisualState.Cancellable) {
            MaterialTheme.colorScheme.onError
        } else {
            MaterialTheme.colorScheme.onPrimary
        },
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "cancelBtnContentColor"
    )

    // Hover is a bounded transition. The previous infinite cancel pulse kept the whole
    // button recomposing for as long as a translation was running.
    val scale = animateFloatAsState(
        targetValue = when {
            !isHovered -> 1f
            visualState == ActionButtonVisualState.Cancellable -> 1.03f
            else -> 1.015f
        },
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "action-button-scale",
    )
    val elevation = animateFloatAsState(
        targetValue = when {
            !isHovered -> 0f
            visualState == ActionButtonVisualState.Cancellable -> 6f
            else -> 2f
        },
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "action-button-elevation",
    )

    Button(
        onClick = {
            if (visualState == ActionButtonVisualState.Cancellable) onCancel?.invoke()
            else onClick()
        },
        enabled = buttonEnabled,
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .hoverable(interactionSource, enabled = buttonEnabled)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                shadowElevation = with(density) { elevation.value.dp.toPx() }
                shape = RoundedCornerShape(12.dp)
                clip = true
            },
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        AnimatedContent(
            targetState = visualState,
            transitionSpec = {
                val enter = fadeIn(animationSpec = motionScheme.defaultEffectsSpec()) +
                    scaleIn(animationSpec = motionScheme.defaultSpatialSpec(), initialScale = 0.92f)
                val exit = fadeOut(animationSpec = motionScheme.fastEffectsSpec()) +
                    scaleOut(animationSpec = motionScheme.fastSpatialSpec(), targetScale = 0.92f)
                enter togetherWith exit
            },
            contentAlignment = Alignment.Center,
            label = "action-button-content",
        ) { state ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (state) {
                    ActionButtonVisualState.Cancellable -> {
                        Icon(
                            Icons.Outlined.Stop,
                            contentDescription = "取消",
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("取消翻译")
                    }

                    ActionButtonVisualState.Running -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("运行中...")
                    }

                    ActionButtonVisualState.Idle -> {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(label)
                    }
                }
            }
        }
    }
}

@Composable
fun TextSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
            maxLines = Int.MAX_VALUE,
            overflow = TextOverflow.Visible
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun LiteratureStyleField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "自定义翻译风格提示词",
    optimizing: Boolean = false,
    onOptimizeClick: (() -> Unit)? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (onOptimizeClick != null) {
                FilledTonalButton(
                    onClick = onOptimizeClick,
                    enabled = !optimizing,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (optimizing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    } else {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("AI 优化")
                    }
                }
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
            minLines = 4,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )
    }
}

@Composable
fun MapInfoFields(
    value: MapInfo,
    onValueChange: (MapInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "地图信息（可选）",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "提供给 AI 以帮助理解地图背景；作者名会被视为不翻译的人名。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ConfigTextField(
            value = value.name.orEmpty(),
            onValueChange = { onValueChange(value.copy(name = it.ifBlank { null })) },
            label = { Text("地图名称") },
            placeholder = { Text("例如：Aetherial Ascent") },
        )
        ConfigTextField(
            value = value.description.orEmpty(),
            onValueChange = { onValueChange(value.copy(description = it.ifBlank { null })) },
            label = { Text("地图简介") },
            placeholder = { Text("简要描述剧情、背景或玩法") },
            singleLine = false,
        )
        ConfigTextField(
            value = value.authors.joinToString(", "),
            onValueChange = { authors ->
                onValueChange(value.copy(authors = authors.split(',').map(String::trim).filter(String::isNotEmpty)))
            },
            label = { Text("地图作者") },
            placeholder = { Text("多个作者请用英文逗号分隔") },
        )
    }
}

@Composable
fun ExtraPromptsField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "额外提示词（可选）",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "会追加到所有 AI 提示词末尾；内容不当可能破坏翻译结果或数据结构。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
            minLines = 4,
            placeholder = { Text("仅填写必须补充的翻译规则或上下文") },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        )
    }
}

@Composable
fun ConfigTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    placeholder: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    modifier: Modifier = Modifier.fillMaxWidth(),
    visualTransformation: VisualTransformation = VisualTransformation.None,
    readOnly: Boolean = false,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = placeholder,
        singleLine = singleLine,
        modifier = modifier,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        readOnly = readOnly,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
        )
    )
}
