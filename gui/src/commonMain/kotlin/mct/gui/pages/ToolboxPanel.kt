package mct.gui.pages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import mct.gui.components.*
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
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("MCT 工具箱", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                "把 CLI 中分散的维护动作集中到可发现、可扩展的工作台。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(title, icon)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tools.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { action -> ToolboxActionCard(action, { onClick(action.operation) }, Modifier.weight(1f)) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ToolboxActionCard(action: ToolboxAction, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier.heightIn(min = 92.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(40.dp)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(action.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(action.title, style = MaterialTheme.typography.titleSmall)
                Text(action.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun ToolboxPanel(
    state: ToolboxState,
    onStateChange: (ToolboxState) -> Unit,
    isRunning: Boolean,
    onRunPointerTest: () -> Unit,
    onRunExportSnbt: () -> Unit,
    onRunOperation: (ToolboxOperation) -> Unit,
) {
    val dirPicker = rememberDirectoryPickerLauncher { file: PlatformFile? ->
        file?.let { onStateChange(state.copy(exportInput = it.absolutePath())) }
    }
    val outputDirPicker = rememberDirectoryPickerLauncher { file: PlatformFile? ->
        file?.let { onStateChange(state.copy(exportOutput = it.absolutePath())) }
    }
    val patternPicker = rememberFilePickerLauncher(
        type = FileKitType.File(), mode = FileKitMode.Single
    ) { file: PlatformFile? -> file?.let { onStateChange(state.copy(pointerPatternPath = it.absolutePath())) } }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ToolboxHero()
        ToolboxSection(
            title = "文本与翻译工具",
            description = "整理文本池、MTLX 映射与语言资源，适合在翻译前后进行可重复处理。",
            icon = Icons.Outlined.Translate,
            onClick = { operation -> onStateChange(state.copy(activeOperation = operation)) },
            tools = listOf(
                ToolboxAction("生成文本池", "把提取结果整理为唯一文本池", Icons.Outlined.AccountTree, ToolboxOperation.FlattenPool),
                ToolboxAction("应用文本映射", "将 mapping 还原为回填替换组", Icons.Outlined.AccountTree, ToolboxOperation.UnflattenPool),
                ToolboxAction("生成 MTLX", "从文本池生成结构化翻译模板", Icons.Outlined.Translate, ToolboxOperation.GenerateMtlx),
                ToolboxAction("翻译 MTLX", "执行 MTLX 映射并保留结构", Icons.Outlined.Translate, ToolboxOperation.TranslateMtlx),
            ),
        )
        ToolboxSection(
            title = "验证与项目工具",
            description = "用统一入口检查规则、生成 schema，并处理项目级资源。",
            icon = Icons.Outlined.Workspaces,
            onClick = { operation -> onStateChange(state.copy(activeOperation = operation)) },
            tools = listOf(
                ToolboxAction("批量替换", "为所有提取文本生成固定替换", Icons.Outlined.FindReplace, ToolboxOperation.ReplaceAll),
                ToolboxAction("导出 Schema", "导出规则 JSON Schema", Icons.Outlined.Schema, ToolboxOperation.ExportSchema),
                ToolboxAction("Command Pattern 测试", "验证命令提取模式与样例输入", Icons.Outlined.GpsFixed, ToolboxOperation.CommandTest),
                ToolboxAction("下载官方语言", "下载指定版本的 Minecraft 语言包", Icons.Outlined.Language, ToolboxOperation.DownloadOfficialLanguage),
                ToolboxAction("合并官方语言", "由两个语言文件生成术语表", Icons.Outlined.Language, ToolboxOperation.CombineOfficialLanguage),
            ),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SectionTitle("指针匹配测试", Icons.Outlined.GpsFixed)
        Text(
            "输入 DataPointer 字符串，测试是否能匹配内置或自定义的过滤规则。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            PointerKind.entries.forEach { pk ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = state.pointerKind == pk,
                        onClick = { onStateChange(state.copy(pointerKind = pk)) }
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(pk.label, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        PathRow(
            "自定义规则 JSON（可选）",
            "留空则仅使用内置规则...",
            state.pointerPatternPath, { onStateChange(state.copy(pointerPatternPath = it)) }
        ) { patternPicker.launch() }
        TextSwitch(
            checked = state.noBuiltin,
            onCheckedChange = { onStateChange(state.copy(noBuiltin = it)) },
            text = "禁用内置规则",
        )
        ConfigTextField(
            value = state.pointerInput,
            onValueChange = { onStateChange(state.copy(pointerResult = null, pointerInput = it)) },
            label = { Text("DataPointer 字符串") },
            placeholder = { Text("例如 >#display>#Name 或 >#>#block_entities>0>#CustomName") },
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                ActionButton("测试匹配", isRunning, onRunPointerTest, enabled = state.pointerInput.isNotBlank())
            }
            if (state.pointerResult != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (state.pointerResult == "true") MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.height(44.dp),
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            state.pointerResult,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (state.pointerResult == "true") MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        SectionTitle("导出 Region SNBT", Icons.Outlined.DataObject)
        Text(
            "将存档中所有 Region 文件的 NBT 数据导出为可读的 SNBT 文本文件，用于调试或审查。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PathRow(
            "Minecraft 存档目录",
            "选择包含 level.dat 的文件夹...",
            state.exportInput,
            { onStateChange(state.copy(exportInput = it)) }) {
            dirPicker.launch()
        }
        PathRow(
            "SNBT 导出目录",
            "选择保存位置...",
            state.exportOutput,
            { onStateChange(state.copy(exportOutput = it)) }) {
            outputDirPicker.launch()
        }

        Spacer(Modifier.height(4.dp))

        ActionButton(
            "导出 SNBT",
            isRunning,
            onRunExportSnbt,
            enabled = state.exportInput.isNotBlank() && state.exportOutput.isNotBlank()
        )
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(operation.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (operation) {
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
                        Text("选择需导出的规则结构。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SchemaKind.entries.forEach { kind ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = state.schemaKind == kind, onClick = { onStateChange(state.copy(schemaKind = kind)) })
                                Text(kind.label, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        PathField("Schema 输出 JSON", state.poolOutput) { onStateChange(state.copy(poolOutput = it)) }
                    }
                    ToolboxOperation.CommandTest -> {
                        PathField("命令样例文件", state.commandInput) { onStateChange(state.copy(commandInput = it)) }
                        PathField("Command Pattern JSON（可选）", state.commandPatternPath) { onStateChange(state.copy(commandPatternPath = it)) }
                        PathField("Command Data Pattern JSON（可选）", state.commandDataPatternPath) { onStateChange(state.copy(commandDataPatternPath = it)) }
                        TextSwitch(state.commandNoBuiltin, { onStateChange(state.copy(commandNoBuiltin = it)) }, "禁用内置规则")
                        if (state.commandResult.isNotBlank()) {
                            Text(state.commandResult, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Button(onClick = onConfirm, enabled = !isRunning && operation.isReady(state)) {
                Text(operation.actionLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isRunning) { Text("取消") } },
    )
}

@Composable
private fun PoolFields(state: ToolboxState, onStateChange: (ToolboxState) -> Unit, showMapping: Boolean) {
    PathField("提取结果 JSON", state.poolInput) { onStateChange(state.copy(poolInput = it)) }
    if (showMapping) PathField("映射 JSON", state.mappingInput) { onStateChange(state.copy(mappingInput = it)) }
    PathField(if (showMapping) "替换输出 JSON" else "文本池输出 JSON", state.poolOutput) { onStateChange(state.copy(poolOutput = it)) }
    if (!showMapping) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            RunMode.entries.forEach { kind ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = state.poolKind == kind, onClick = { onStateChange(state.copy(poolKind = kind)) })
                    Text(kind.label, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        TextSwitch(state.poolSimply, { onStateChange(state.copy(poolSimply = it)) }, "使用简单文本池模式")
    }
}

@Composable
private fun PathField(label: String, value: String, onValueChange: (String) -> Unit) =
    ConfigTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, singleLine = true)

private fun ToolboxOperation.isReady(state: ToolboxState): Boolean = when (this) {
    ToolboxOperation.FlattenPool -> state.poolInput.isNotBlank() && state.poolOutput.isNotBlank()
    ToolboxOperation.UnflattenPool -> state.poolInput.isNotBlank() && state.mappingInput.isNotBlank() && state.poolOutput.isNotBlank()
    ToolboxOperation.GenerateMtlx -> state.poolInput.isNotBlank() && state.poolOutput.isNotBlank()
    ToolboxOperation.TranslateMtlx -> state.mtlxInput.isNotBlank() && state.poolInput.isNotBlank() && state.poolOutput.isNotBlank()
    ToolboxOperation.ReplaceAll -> state.poolInput.isNotBlank() && state.poolOutput.isNotBlank()
    ToolboxOperation.ExportSchema -> state.poolOutput.isNotBlank()
    ToolboxOperation.CommandTest -> state.commandInput.isNotBlank()
    ToolboxOperation.DownloadOfficialLanguage -> state.officialMinecraftVersion.isNotBlank() && state.officialOutput.isNotBlank() && state.officialConcurrency.toIntOrNull() in 1..64
    ToolboxOperation.CombineOfficialLanguage -> state.officialSourceLanguage.isNotBlank() && state.officialTargetLanguage.isNotBlank() && state.poolOutput.isNotBlank()
}
