package mct.gui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import mct.gui.model.MCTPatternEntry
import mct.gui.model.MCTPatternSlot
import mct.gui.model.MCTPatternState

/** 展开规则列表的动画时长；列表较高，收尾放慢一些。 */
private const val ENTER_EXPAND_MILLIS = 480
private const val ENTER_FADE_MILLIS = 220

/**
 * 统一的 `MCTPattern` 规则编辑器。
 *
 * [slots] 决定当前场景展示哪些规则类别；每一类都可单独配置规则文件、是否合并内置规则、
 * 是否启用规则过滤。参数较多，因此整体默认折叠，折叠时给出摘要。
 */
@Composable
fun MCTPatternEditor(
    patterns: MCTPatternState,
    onPatternsChange: (MCTPatternState) -> Unit,
    slots: List<MCTPatternSlot>,
    modifier: Modifier = Modifier,
    title: String = "规则配置",
    initiallyExpanded: Boolean = false,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val motionScheme = MaterialTheme.motionScheme

    val customized = slots.count { patterns[it].path.isNotBlank() }
    val unfiltered = slots.count { it.filterToggle && !patterns[it].filtering }
    val summary = buildString {
        append(if (customized == 0) "全部使用内置规则" else "已自定义 $customized 类规则")
        if (unfiltered > 0) append(" · 已关闭 $unfiltered 类过滤")
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ElevatedCard(
            onClick = { expanded = !expanded },
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Rule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "收起规则配置" else "展开规则配置",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            // 展开的是一整块规则列表，用偏慢的减速动画收尾，避免高度变化过急。
            enter = fadeIn(animationSpec = tween(durationMillis = ENTER_FADE_MILLIS)) +
                expandVertically(
                    animationSpec = tween(
                        durationMillis = ENTER_EXPAND_MILLIS,
                        easing = FastOutSlowInEasing,
                    )
                ),
            exit = fadeOut(animationSpec = motionScheme.fastEffectsSpec()) +
                shrinkVertically(animationSpec = motionScheme.fastSpatialSpec()),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                slots.forEach { slot ->
                    MCTPatternSlotCard(
                        slot = slot,
                        entry = patterns[slot],
                        onEntryChange = { onPatternsChange(patterns.with(slot, it)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MCTPatternSlotCard(
    slot: MCTPatternSlot,
    entry: MCTPatternEntry,
    onEntryChange: (MCTPatternEntry) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    slot.label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (entry.path.isNotBlank()) StatusPill("自定义")
            }
            Text(
                slot.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (slot.filterToggle) {
                TextSwitch(
                    checked = entry.filtering,
                    onCheckedChange = { onEntryChange(entry.copy(filtering = it)) },
                    text = "启用规则过滤（关闭则提取全部文本）",
                )
            }

            if (!slot.filterToggle || entry.filtering) {
                PatternFileRow(
                    label = "规则文件 JSON",
                    value = entry.path,
                    onValueChange = { path ->
                        onEntryChange(
                            if (path.isBlank()) entry.copy(path = "", useBuiltin = true)
                            else entry.copy(path = path)
                        )
                    },
                )
                if (slot.builtinToggle && entry.path.isNotBlank()) {
                    TextSwitch(
                        checked = entry.useBuiltin,
                        onCheckedChange = { onEntryChange(entry.copy(useBuiltin = it)) },
                        text = "合并内置规则（关闭则仅使用自定义规则）",
                    )
                }
            }
        }
    }
}

/** 规则文件选择行；留空表示该类别回退到内置规则。 */
@Composable
private fun PatternFileRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "留空则使用内置规则...",
) {
    val picker = rememberFilePickerLauncher(
        type = FileKitType.File(), mode = FileKitMode.Single,
    ) { file: PlatformFile? ->
        file?.let { onValueChange(it.absolutePath()) }
    }
    PathRow(label, placeholder, value, onValueChange) { picker.launch() }
}

/** 小号状态标签，用于提示该类规则已被自定义。 */
@Composable
private fun StatusPill(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}
