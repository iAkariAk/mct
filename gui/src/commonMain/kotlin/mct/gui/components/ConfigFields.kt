package mct.gui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import mct.gui.platform.platformPathOf
import mct.gui.state.ProjectPatternSlotEditor
import mct.gui.util.oneDecimal

/**
 * Show [hint] as a plain tooltip while the pointer hovers the wrapped content.
 *
 * Used for controls that have no room for a supporting line (switches, sliders, segmented groups);
 * text fields show the same hint as their supporting text instead.
 */
@Composable
fun HoverHint(
    hint: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(hint) } },
        state = rememberTooltipState(),
        modifier = modifier,
        content = content,
    )
}

/**
 * A titled group of configuration fields.
 *
 * Nested one tone below the function area's card, matching how the rest of the GUI layers its
 * sections. [trailing] sits opposite the title for group-level actions.
 */
@Composable
fun ConfigGroup(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(title, icon)
                if (trailing != null) {
                    Spacer(Modifier.weight(1f))
                    trailing()
                }
            }
            content()
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
)

/**
 * A `mct.toml` string field, with the CLI's comment for it as the hint under the field.
 *
 * [value] is a [State] rather than a plain string so the field subscribes to its own slice of the
 * editor: typing here recomposes this field and nothing else in the form.
 */
@Composable
fun ConfigTextField(
    label: String,
    hint: String,
    value: State<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    monospace: Boolean = false,
    placeholder: String? = null,
) = ConfigTextFieldBody(
    label = label,
    hint = hint,
    value = value.value,
    onValueChange = onValueChange,
    modifier = modifier,
    singleLine = singleLine,
    minLines = minLines,
    monospace = monospace,
    placeholder = placeholder,
)

/**
 * A `mct.toml` string field whose value may be absent, i.e. `null` in the file.
 *
 * Separate from [ConfigTextField] rather than an overload: two overloads differing only in
 * `State<String>` versus `State<String?>` erase to the same JVM signature.
 */
@Composable
fun ConfigNullableTextField(
    label: String,
    hint: String,
    value: State<String?>,
    onValueChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    monospace: Boolean = false,
    placeholder: String? = null,
) = ConfigTextFieldBody(
    label = label,
    hint = hint,
    value = value.value.orEmpty(),
    onValueChange = { entered -> onValueChange(entered.ifBlank { null }) },
    modifier = modifier,
    singleLine = singleLine,
    minLines = minLines,
    monospace = monospace,
    placeholder = placeholder,
)

@Composable
private fun ConfigTextFieldBody(
    label: String,
    hint: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    monospace: Boolean = false,
    placeholder: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = { Text(hint, style = MaterialTheme.typography.bodySmall) },
        singleLine = singleLine,
        minLines = minLines,
        textStyle = if (monospace) {
            MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
        } else {
            LocalTextStyle.current
        },
        colors = fieldColors(),
    )
}

/** A `mct.toml` integer field. Deliberately a field, not a slider: these values have no upper bound. */
@Composable
fun ConfigIntField(
    label: String,
    hint: String,
    value: State<Int>,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = value.value
    // Local text so a half-typed number ("1" on the way to "12") is not normalised back; the field
    // only re-syncs when the value changed somewhere else (reload, revert, another control).
    var text by remember { mutableStateOf(current.toString()) }
    LaunchedEffect(current) {
        if (text.toIntOrNull() != current) text = current.toString()
    }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val digits = raw.filter(Char::isDigit).take(9)
            text = digits
            digits.toIntOrNull()?.let(onValueChange)
        },
        modifier = modifier
            .fillMaxWidth()
            // An emptied box stores nothing (there is nothing to parse), so the stored number has to
            // become visible again instead of the field claiming a change that never happened.
            .onFocusChanged { focus ->
                if (!focus.isFocused && text.toIntOrNull() == null) text = current.toString()
            },
        label = { Text(label) },
        supportingText = { Text(hint, style = MaterialTheme.typography.bodySmall) },
        singleLine = true,
        colors = fieldColors(),
    )
}

/** A `mct.toml` boolean, shown as a switch with the CLI's comment as a hover tooltip. */
@Composable
fun ConfigSwitchRow(
    label: String,
    hint: String,
    checked: State<Boolean>,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HoverHint(hint, Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 16.dp),
            )
        }
        Switch(checked = checked.value, onCheckedChange = onCheckedChange)
    }
}

/**
 * A bounded `mct.toml` number, shown as a slider.
 *
 * Only fields with a real range use this; an absent value (`null` in the file) is a switch away
 * rather than a slider position, because "no value" is not on the scale.
 */
@Composable
fun ConfigSliderField(
    label: String,
    hint: String,
    value: State<Double?>,
    onValueChange: (Double?) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..2f,
    steps: Int = 19,
) {
    val current = value.value
    val useDefault = remember { derivedStateOf { value.value == null } }
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HoverHint(hint, Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(end = 16.dp),
                )
            }
            Text(
                current?.let { oneDecimal(it) } ?: "默认",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (current != null) {
            Slider(
                value = current.toFloat().coerceIn(valueRange),
                onValueChange = { onValueChange((kotlin.math.round(it * 10f) / 10f).toDouble()) },
                valueRange = valueRange,
                steps = steps,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (useDefault.value) "使用模型默认值" else "自定义",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = useDefault.value,
                onCheckedChange = { useModelDefault ->
                    onValueChange(
                        if (useModelDefault) null
                        else ((valueRange.start + valueRange.endInclusive) / 2f).toDouble()
                    )
                },
            )
        }
    }
}

/** A small closed set of `mct.toml` values, shown as the app's connected toggle group. */
@Composable
fun <T> ConfigButtonGroupRow(
    label: String,
    hint: String,
    entries: List<T>,
    selected: State<T>,
    entryLabel: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HoverHint(hint) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
        EnumButtonGroup(
            entries = entries,
            selected = selected.value,
            label = entryLabel,
            onSelected = onSelected,
        )
    }
}

/**
 * One extraction-pattern category: its rule files plus, where the CLI supports it, the switch that
 * decides whether the built-in patterns of that category are merged into the custom ones.
 *
 * Picked files are stored as absolute paths on purpose. Pattern paths are the one kind of path in
 * `mct.toml` the CLI resolves against its *working directory* rather than the project root (other
 * paths are joined onto `--project-dir`), so a path relative to the project would make
 * `project update` fail — or worse, silently read a same-named file from wherever the CLI was
 * started. An absolute path is read the same way before and after that resolution is fixed.
 *
 * Separators are written as `/`, which the CLI reads on every platform: ktoml's writer does not
 * escape a backslash that precedes a `u` or `U`, so a Windows path such as `C:\Users\...` would be
 * written unreadable.
 */
@Composable
fun ConfigPatternSlotField(
    slot: ProjectPatternSlotEditor,
    modifier: Modifier = Modifier,
) {
    val paths = slot.paths.value
    val picker = rememberFilePickerLauncher(type = FileKitType.File(listOf("json"))) { file: PlatformFile? ->
        file?.let { slot.addPath(platformPathOf(it).replace('\\', '/')) }
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HoverHint("patterns.${slot.slot.key} — ${slot.slot.hint}", Modifier.weight(1f)) {
                Text(
                    slot.slot.label,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
            if (slot.slot.supportsBuiltin) {
                HoverHint(
                    slot.slot.builtinNote
                        ?: "patterns.${slot.slot.key}.has_builtin — 与该类的内置规则合并；关闭后只使用上面列出的文件",
                ) {
                    Text(
                        "内置",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                Switch(checked = slot.hasBuiltin.value, onCheckedChange = slot::setHasBuiltin)
            }
        }
        slot.slot.builtinNote?.let { note ->
            // Inline, not only in the tooltip: this switch is ignored by the CLI as it stands, and a
            // control that silently does nothing is exactly what the note exists to prevent.
            Text(
                note,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (paths.isEmpty()) {
            Text(
                "未指定规则文件，仅使用内置规则",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            paths.forEach { path ->
                key(path) {
                    // Resolved exactly like the CLI resolves it, i.e. relative to the process'
                    // working directory, so a path the CLI cannot read is flagged here first.
                    val missing = rememberMissingPath(path, mustExist = true)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    path,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (missing) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                                if (missing) {
                                    Text(
                                        "文件不存在（CLI 按自身工作目录解析该路径）",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            IconButton(onClick = { slot.removePath(path) }) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = "移除 $path",
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        TextButton(onClick = { picker.launch() }, shapes = ButtonDefaults.shapes()) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("添加规则文件")
        }
    }
}
