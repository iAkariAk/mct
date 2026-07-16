package mct.gui.pages

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.automirrored.outlined.MergeType
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import mct.gui.components.ConfigTextField
import mct.gui.components.PathRow
import mct.gui.components.SectionTitle
import mct.gui.components.TextSwitch
import mct.gui.model.*

private data class ToolboxAction(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val operation: ToolboxOperation,
)

@Composable
private fun ToolboxHero(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primary,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.Handyman,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "MCT 工具箱",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun ToolboxSection(
    title: String,
    description: String,
    icon: ImageVector,
    tools: List<ToolboxAction>,
    onClick: (ToolboxOperation) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(title, icon)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = when {
                maxWidth >= 720.dp -> 3
                maxWidth >= 420.dp -> 2
                else -> 1
            }
            val spacing = 12.dp
            val cardWidth = (maxWidth - spacing * (columns - 1)) / columns
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                maxItemsInEachRow = columns,
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalArrangement = Arrangement.spacedBy(spacing),
            ) {
                tools.forEach { action ->
                    ToolboxActionCard(
                        action = action,
                        onClick = { onClick(action.operation) },
                        modifier = Modifier.width(cardWidth),
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolboxActionCard(
    action: ToolboxAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.heightIn(min = 132.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            action.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
                Text(action.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
fun ToolboxPanel(
    state: ToolboxState,
    onStateChange: (ToolboxState) -> Unit,
    isRunning: Boolean,
    onRunOperation: (ToolboxOperation) -> Unit,
    modifier: Modifier = Modifier,
) {
    val openOperation: (ToolboxOperation) -> Unit = { operation ->
        onStateChange(state.copy(activeOperation = operation))
    }

    Column(modifier = modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        ToolboxHero()
        ToolboxSection(
            title = "文本与翻译",
            description = "整理文本池、映射和 MTLX，让翻译前后的数据转换保持可重复。",
            icon = Icons.Outlined.Translate,
            onClick = openOperation,
            tools = listOf(
                ToolboxAction(
                    "生成文本池",
                    "把提取结果整理为唯一文本池。",
                    Icons.Outlined.AccountTree,
                    ToolboxOperation.FlattenPool
                ),
                ToolboxAction(
                    "应用文本映射",
                    "把 mapping 还原为回填替换组。",
                    Icons.AutoMirrored.Outlined.MergeType,
                    ToolboxOperation.UnflattenPool
                ),
                ToolboxAction(
                    "生成 MTLX",
                    "从文本池生成结构化翻译模板。",
                    Icons.Outlined.Description,
                    ToolboxOperation.GenerateMtlx
                ),
                ToolboxAction(
                    "翻译 MTLX",
                    "执行 MTLX 映射并保留原有结构。",
                    Icons.Outlined.Translate,
                    ToolboxOperation.TranslateMtlx
                ),
                ToolboxAction(
                    "批量替换",
                    "为所有提取文本生成固定替换。",
                    Icons.Outlined.FindReplace,
                    ToolboxOperation.ReplaceAll
                ),
            ),
        )
        ToolboxSection(
            title = "规则与数据检查",
            description = "集中测试匹配规则、导出 schema，并检查存档中的原始 NBT 数据。",
            icon = Icons.AutoMirrored.Outlined.Rule,
            onClick = openOperation,
            tools = listOf(
                ToolboxAction(
                    "DataPointer 测试",
                    "验证内置或自定义指针过滤规则。",
                    Icons.Outlined.GpsFixed,
                    ToolboxOperation.PointerTest
                ),
                ToolboxAction(
                    "Command Pattern 测试",
                    "用样例输入验证命令提取模式。",
                    Icons.Outlined.Terminal,
                    ToolboxOperation.CommandTest
                ),
                ToolboxAction(
                    "导出 Schema",
                    "导出规则配置使用的 JSON Schema。",
                    Icons.Outlined.Schema,
                    ToolboxOperation.ExportSchema
                ),
                ToolboxAction(
                    "导出 Region SNBT",
                    "把 Region NBT 导出为可读 SNBT。",
                    Icons.Outlined.DataObject,
                    ToolboxOperation.ExportSnbt
                ),
            ),
        )
        ToolboxSection(
            title = "官方语言资源",
            description = "下载 Minecraft 官方语言文件，或把两种语言合并为术语表。",
            icon = Icons.Outlined.Language,
            onClick = openOperation,
            tools = listOf(
                ToolboxAction(
                    "下载官方语言",
                    "获取指定版本的官方语言资源。",
                    Icons.Outlined.Download,
                    ToolboxOperation.DownloadOfficialLanguage
                ),
                ToolboxAction(
                    "合并官方语言",
                    "由源语言和目标语言生成术语表。",
                    Icons.AutoMirrored.Outlined.CompareArrows,
                    ToolboxOperation.CombineOfficialLanguage
                ),
            ),
        )
    }

    state.activeOperation?.let { operation ->
        ToolboxOperationDialog(
            operation = operation,
            state = state,
            isRunning = isRunning,
            onStateChange = onStateChange,
            onDismiss = { onStateChange(state.copy(activeOperation = null)) },
            onConfirm = { onRunOperation(operation) },
        )
    }
}

@Composable
private fun ToolboxOperationDialog(
    operation: ToolboxOperation,
    state: ToolboxState,
    isRunning: Boolean,
    onStateChange: (ToolboxState) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val pointerPatternPicker = rememberFilePickerLauncher(
        type = FileKitType.File(),
        mode = FileKitMode.Single,
    ) { file: PlatformFile? ->
        file?.let { onStateChange(state.copy(pointerPatternPath = it.absolutePath())) }
    }
    val exportInputPicker = rememberDirectoryPickerLauncher { file: PlatformFile? ->
        file?.let { onStateChange(state.copy(exportInput = it.absolutePath())) }
    }
    val exportOutputPicker = rememberDirectoryPickerLauncher { file: PlatformFile? ->
        file?.let { onStateChange(state.copy(exportOutput = it.absolutePath())) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(operation.icon(), contentDescription = null) },
        title = { Text(operation.title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (operation) {
                    ToolboxOperation.PointerTest -> {
                        EnumSegmentedButtons(
                            entries = PointerKind.entries,
                            selected = state.pointerKind,
                            label = { it.label },
                            onSelected = { onStateChange(state.copy(pointerKind = it, pointerResult = null)) },
                        )
                        PathRow(
                            label = "自定义规则 JSON（可选）",
                            placeholder = "留空则仅使用内置规则",
                            value = state.pointerPatternPath,
                            onValueChange = {
                                onStateChange(
                                    state.copy(
                                        pointerPatternPath = it,
                                        pointerResult = null
                                    )
                                )
                            },
                            onBrowse = { pointerPatternPicker.launch() },
                        )
                        TextSwitch(
                            checked = state.noBuiltin,
                            onCheckedChange = { onStateChange(state.copy(noBuiltin = it, pointerResult = null)) },
                            text = "禁用内置规则",
                        )
                        ConfigTextField(
                            value = state.pointerInput,
                            onValueChange = { onStateChange(state.copy(pointerInput = it, pointerResult = null)) },
                            label = { Text("DataPointer 字符串") },
                            placeholder = { Text("例如 >#display>#Name") },
                            singleLine = true,
                        )
                        AnimatedVisibility(
                            visible = state.pointerResult != null,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            val matched = state.pointerResult == "true"
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large,
                                color = if (matched) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(
                                        if (matched) Icons.Outlined.CheckCircle else Icons.Outlined.Cancel,
                                        contentDescription = null,
                                    )
                                    Text(if (matched) "匹配成功" else "未匹配")
                                }
                            }
                        }
                    }

                    ToolboxOperation.ExportSnbt -> {
                        Text(
                            "将存档内的 Region NBT 导出为可读 SNBT，便于调试和审查。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        PathRow(
                            label = "Minecraft 存档目录",
                            placeholder = "选择包含 level.dat 的目录",
                            value = state.exportInput,
                            onValueChange = { onStateChange(state.copy(exportInput = it)) },
                            onBrowse = { exportInputPicker.launch() },
                        )
                        PathRow(
                            label = "SNBT 导出目录",
                            placeholder = "选择保存位置",
                            value = state.exportOutput,
                            onValueChange = { onStateChange(state.copy(exportOutput = it)) },
                            onBrowse = { exportOutputPicker.launch() },
                        )
                    }

                    ToolboxOperation.FlattenPool -> PoolFields(state, onStateChange, showMapping = false)
                    ToolboxOperation.UnflattenPool -> PoolFields(state, onStateChange, showMapping = true)
                    ToolboxOperation.GenerateMtlx -> {
                        PathField("文本池 JSON", state.poolInput) { onStateChange(state.copy(poolInput = it)) }
                        PathField("MTLX 输出文件", state.poolOutput) { onStateChange(state.copy(poolOutput = it)) }
                    }
                    ToolboxOperation.TranslateMtlx -> {
                        PathField("MTLX 文件", state.mtlxInput) { onStateChange(state.copy(mtlxInput = it)) }
                        PathField("文本池 JSON", state.poolInput) { onStateChange(state.copy(poolInput = it)) }
                        PathField("映射输出 JSON", state.poolOutput) { onStateChange(state.copy(poolOutput = it)) }
                    }
                    ToolboxOperation.ReplaceAll -> {
                        PathField("提取结果 JSON", state.poolInput) { onStateChange(state.copy(poolInput = it)) }
                        PathField("替换输出 JSON", state.poolOutput) { onStateChange(state.copy(poolOutput = it)) }
                        ConfigTextField(
                            value = state.replacement,
                            onValueChange = { onStateChange(state.copy(replacement = it)) },
                            label = { Text("固定替换内容") },
                            singleLine = true,
                        )
                    }
                    ToolboxOperation.ExportSchema -> {
                        Text(
                            "选择需要导出的规则结构。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        EnumSegmentedButtons(
                            entries = SchemaKind.entries,
                            selected = state.schemaKind,
                            label = { it.label },
                            onSelected = { onStateChange(state.copy(schemaKind = it)) },
                        )
                        PathField("Schema 输出 JSON", state.poolOutput) { onStateChange(state.copy(poolOutput = it)) }
                    }
                    ToolboxOperation.CommandTest -> {
                        PathField("命令样例文件", state.commandInput) { onStateChange(state.copy(commandInput = it)) }
                        PathField("Command Pattern JSON（可选）", state.commandPatternPath) { onStateChange(state.copy(commandPatternPath = it)) }
                        PathField("Command Data Pattern JSON（可选）", state.commandDataPatternPath) { onStateChange(state.copy(commandDataPatternPath = it)) }
                        TextSwitch(state.commandNoBuiltin, { onStateChange(state.copy(commandNoBuiltin = it)) }, "禁用内置规则")
                        AnimatedVisibility(visible = state.commandResult.isNotBlank()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ) {
                                Text(
                                    state.commandResult,
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                    ToolboxOperation.DownloadOfficialLanguage -> {
                        ConfigTextField(
                            value = state.officialMinecraftVersion,
                            onValueChange = { onStateChange(state.copy(officialMinecraftVersion = it)) },
                            label = { Text("Minecraft 版本") },
                            placeholder = { Text("latest 或具体版本，如 1.21.5") },
                            singleLine = true,
                        )
                        PathField("语言包输出目录", state.officialOutput) { onStateChange(state.copy(officialOutput = it)) }
                        ConfigTextField(
                            value = state.officialConcurrency,
                            onValueChange = { onStateChange(state.copy(officialConcurrency = it)) },
                            label = { Text("下载并发数") },
                            singleLine = true,
                        )
                    }
                    ToolboxOperation.CombineOfficialLanguage -> {
                        PathField("源语言 JSON", state.officialSourceLanguage) { onStateChange(state.copy(officialSourceLanguage = it)) }
                        PathField("目标语言 JSON", state.officialTargetLanguage) { onStateChange(state.copy(officialTargetLanguage = it)) }
                        PathField("术语表输出 JSON", state.poolOutput) { onStateChange(state.copy(poolOutput = it)) }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isRunning && operation.isReady(state),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (isRunning) "执行中" else operation.actionLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isRunning, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("关闭")
            }
        },
    )
}

@Composable
private fun <T> EnumSegmentedButtons(
    entries: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        entries.forEachIndexed { index, entry ->
            SegmentedButton(
                selected = selected == entry,
                onClick = { onSelected(entry) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = entries.size),
                label = { Text(label(entry)) },
            )
        }
    }
}

@Composable
private fun PoolFields(
    state: ToolboxState,
    onStateChange: (ToolboxState) -> Unit,
    showMapping: Boolean,
) {
    PathField("提取结果 JSON", state.poolInput) { onStateChange(state.copy(poolInput = it)) }
    if (showMapping) PathField("映射 JSON", state.mappingInput) { onStateChange(state.copy(mappingInput = it)) }
    PathField(if (showMapping) "替换输出 JSON" else "文本池输出 JSON", state.poolOutput) {
        onStateChange(state.copy(poolOutput = it))
    }
    if (!showMapping) {
        EnumSegmentedButtons(
            entries = RunMode.entries,
            selected = state.poolKind,
            label = { it.label },
            onSelected = { onStateChange(state.copy(poolKind = it)) },
        )
        TextSwitch(state.poolSimply, { onStateChange(state.copy(poolSimply = it)) }, "使用简单文本池模式")
    }
}

@Composable
private fun PathField(label: String, value: String, onValueChange: (String) -> Unit) {
    ConfigTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
    )
}

private fun ToolboxOperation.icon(): ImageVector = when (this) {
    ToolboxOperation.PointerTest -> Icons.Outlined.GpsFixed
    ToolboxOperation.ExportSnbt -> Icons.Outlined.DataObject
    ToolboxOperation.FlattenPool -> Icons.Outlined.AccountTree
    ToolboxOperation.UnflattenPool -> Icons.AutoMirrored.Outlined.MergeType
    ToolboxOperation.GenerateMtlx -> Icons.Outlined.Description
    ToolboxOperation.TranslateMtlx -> Icons.Outlined.Translate
    ToolboxOperation.ReplaceAll -> Icons.Outlined.FindReplace
    ToolboxOperation.ExportSchema -> Icons.Outlined.Schema
    ToolboxOperation.CommandTest -> Icons.Outlined.Terminal
    ToolboxOperation.DownloadOfficialLanguage -> Icons.Outlined.Download
    ToolboxOperation.CombineOfficialLanguage -> Icons.AutoMirrored.Outlined.CompareArrows
}

private fun ToolboxOperation.isReady(state: ToolboxState): Boolean = when (this) {
    ToolboxOperation.PointerTest -> state.pointerInput.isNotBlank()
    ToolboxOperation.ExportSnbt -> state.exportInput.isNotBlank() && state.exportOutput.isNotBlank()
    ToolboxOperation.FlattenPool -> state.poolInput.isNotBlank() && state.poolOutput.isNotBlank()
    ToolboxOperation.UnflattenPool -> state.poolInput.isNotBlank() && state.mappingInput.isNotBlank() && state.poolOutput.isNotBlank()
    ToolboxOperation.GenerateMtlx -> state.poolInput.isNotBlank() && state.poolOutput.isNotBlank()
    ToolboxOperation.TranslateMtlx -> state.mtlxInput.isNotBlank() && state.poolInput.isNotBlank() && state.poolOutput.isNotBlank()
    ToolboxOperation.ReplaceAll -> state.poolInput.isNotBlank() && state.poolOutput.isNotBlank()
    ToolboxOperation.ExportSchema -> state.poolOutput.isNotBlank()
    ToolboxOperation.CommandTest -> state.commandInput.isNotBlank()
    ToolboxOperation.DownloadOfficialLanguage -> state.officialMinecraftVersion.isNotBlank() &&
            state.officialOutput.isNotBlank() && state.officialConcurrency.toIntOrNull() in 1..64

    ToolboxOperation.CombineOfficialLanguage -> state.officialSourceLanguage.isNotBlank() &&
            state.officialTargetLanguage.isNotBlank() && state.poolOutput.isNotBlank()
}
