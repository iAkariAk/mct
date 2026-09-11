package mct.gui.components

import androidx.compose.animation.*
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

/**
 * The single editor for `MCTPattern` rules.
 *
 * [slots] selects which rule categories this context shows. Each category is configured
 * independently: a rule file, whether to merge the built-in rules, and whether its filter
 * is enabled. That is a lot of configuration, so the editor starts collapsed and shows a
 * summary while collapsed.
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

    // The summary depends only on rule content, so editing a rule while collapsed neither
    // recomputes it nor recomposes this header.
    val summary = remember(slots, patterns) {
        val customized = slots.count { patterns[it].path.isNotBlank() }
        val unfiltered = slots.count { it.filterToggle && !patterns[it].filtering }
        buildString {
            append(if (customized == 0) "全部使用内置规则" else "已自定义 $customized 类规则")
            if (unfiltered > 0) append(" · 已关闭 $unfiltered 类过滤")
        }
    }

    // Stable callback identity: otherwise each category's callback is rebuilt whenever
    // `patterns` changes, recomposing every card at once.
    val currentPatterns by rememberUpdatedState(patterns)
    val updateSlot: (MCTPatternSlot, MCTPatternEntry) -> Unit = remember(onPatternsChange) {
        { slot, entry -> onPatternsChange(currentPatterns.with(slot, entry)) }
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
            // A whole rule list expands here; the slow motion-scheme specs keep it from snapping.
            enter = fadeIn(animationSpec = motionScheme.slowEffectsSpec()) +
                expandVertically(animationSpec = motionScheme.slowSpatialSpec()),
            exit = fadeOut(animationSpec = motionScheme.fastEffectsSpec()) +
                shrinkVertically(animationSpec = motionScheme.fastSpatialSpec()),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                slots.forEach { slot ->
                    MCTPatternSlotCard(
                        slot = slot,
                        entry = patterns[slot],
                        onEntryChange = remember(slot, updateSlot) {
                            { entry -> updateSlot(slot, entry) }
                        },
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

/** Rule-file picker row; blank means this category falls back to the built-in rules. */
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

/** Small status pill marking a category as customized. */
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
