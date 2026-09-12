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
import androidx.compose.runtime.*
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("浏览")
            }
        }
    }
}

/**
 * A titled group of related controls.
 *
 * Panels are assembled from these cards instead of loose dividers: the content sits on a tonal
 * surface with the same icon/title header, keeping every section visually consistent.
 */
@Composable
fun PanelSection(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionTitle(title, icon)
            content()
        }
    }
}

/**
 * Equal-width single-select button group.
 *
 * Material 3 Expressive replaces segmented buttons with the *connected button group*, which
 * applies shape morph when a button is pressed and when it becomes selected. Buttons are laid
 * out with equal weight, so the group always fills its row without manual width arithmetic.
 *
 * Implemented on the public [ButtonGroup] API: each entry is a registered [customItem] whose
 * content is a [ToggleButton] using the leading / middle / trailing connected shapes.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> EnumButtonGroup(
    entries: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    ButtonGroup(
        // Items carry `weight`, so they normally share the row exactly. The indicator is kept
        // wired because the group moves items into its menu when they genuinely cannot fit --
        // without it those options would silently disappear.
        overflowIndicator = { menuState -> ButtonGroupDefaults.OverflowIndicator(menuState = menuState) },
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        val scope = this
        val single = entries.size == 1
        entries.forEachIndexed { index, entry ->
            customItem(
                buttonGroupContent = {
                    val interactionSource = remember { MutableInteractionSource() }
                    // A lone entry has no neighbours to connect to, so it keeps the regular
                    // toggle-button shape instead of the asymmetric leading shape.
                    val shapes = when {
                        single -> ToggleButtonDefaults.shapesFor(ButtonDefaults.MinHeight)
                        index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        index == entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    }
                    ToggleButton(
                        checked = selected == entry,
                        onCheckedChange = { checked -> if (checked) onSelected(entry) },
                        shapes = shapes,
                        interactionSource = interactionSource,
                        modifier = with(scope) {
                            Modifier.animateWidth(interactionSource).weight(1f)
                        },
                    ) {
                        Text(label(entry), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                menuContent = { menuState ->
                    DropdownMenuItem(
                        text = { Text(label(entry)) },
                        onClick = {
                            onSelected(entry)
                            menuState.dismiss()
                        },
                    )
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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

    val shapes = ButtonDefaults.shapes()

    Button(
        onClick = {
            if (visualState == ActionButtonVisualState.Cancellable) onCancel?.invoke()
            else onClick()
        },
        // Expressive button shapes: the container morphs to `pressedShape` while pressed.
        shapes = shapes,
        enabled = buttonEnabled,
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .hoverable(interactionSource, enabled = buttonEnabled)
            .graphicsLayer {
                // This block re-runs on every frame of the hover animation, so nothing
                // allocatable belongs here.
                scaleX = scale.value
                scaleY = scale.value
                shadowElevation = with(density) { elevation.value.dp.toPx() }
                this.shape = shapes.shape
                clip = true
            },
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
                        LoadingIndicator(
                            modifier = Modifier.size(20.dp),
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
                    shapes = ButtonDefaults.shapes(),
                ) {
                    if (optimizing) {
                        LoadingIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
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
    // Raw text is kept locally so a half-typed value (a trailing ", ", a lone space) is not
    // normalized away while the user is still typing; the parsed form is only what is emitted.
    var name by remember { mutableStateOf(value.name.orEmpty()) }
    var description by remember { mutableStateOf(value.description.orEmpty()) }
    var authors by remember { mutableStateOf(value.authors.joinToString(", ")) }

    LaunchedEffect(value.name) { value.name.orEmpty().let { if (it != name.trim()) name = it } }
    LaunchedEffect(value.description) {
        value.description.orEmpty().let { if (it != description.trim()) description = it }
    }
    LaunchedEffect(value.authors) {
        val parsed = authors.split(',').map(String::trim).filter(String::isNotEmpty)
        if (parsed != value.authors) authors = value.authors.joinToString(", ")
    }

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
            value = name,
            onValueChange = {
                name = it
                onValueChange(value.copy(name = it.trim().ifBlank { null }))
            },
            label = { Text("地图名称") },
            placeholder = { Text("例如：Aetherial Ascent") },
        )
        ConfigTextField(
            value = description,
            onValueChange = {
                description = it
                onValueChange(value.copy(description = it.trim().ifBlank { null }))
            },
            label = { Text("地图简介") },
            placeholder = { Text("简要描述剧情、背景或玩法") },
            singleLine = false,
        )
        ConfigTextField(
            value = authors,
            onValueChange = {
                authors = it
                onValueChange(
                    value.copy(
                        authors = it.split(',').map(String::trim).filter(String::isNotEmpty)
                    )
                )
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
