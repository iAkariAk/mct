package mct.gui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// ── 标签页 ①：提取 ────────────────────────────────────────────

@Composable
fun ExtractPanel(
    state: ExtractState,
    onStateChange: (ExtractState) -> Unit,
    isRunning: Boolean,
    onRun: () -> Unit
) {
    val dirPicker = rememberDirectoryPickerLauncher { file: PlatformFile? ->
        file?.let { onStateChange(state.copy(input = it.absolutePath())) }
    }
    val fileSaver = rememberFileSaverLauncher(FileKitDialogSettings.createDefault()) { file: PlatformFile? ->
        file?.let { onStateChange(state.copy(output = ensureJsonExt(it.absolutePath()))) }
    }
    val patternPicker = rememberFilePickerLauncher(
        type = FileKitType.File(), mode = FileKitMode.Single
    ) { file: PlatformFile? -> file?.let { onStateChange(state.copy(regionPatternPath = it.absolutePath())) } }
    val mcfPatternPicker = rememberFilePickerLauncher(
        type = FileKitType.File(), mode = FileKitMode.Single
    ) { file: PlatformFile? -> file?.let { onStateChange(state.copy(mcfPatternPath = it.absolutePath())) } }
    val mcjPatternPicker = rememberFilePickerLauncher(
        type = FileKitType.File(), mode = FileKitMode.Single
    ) { file: PlatformFile? -> file?.let { onStateChange(state.copy(mcjPatternPath = it.absolutePath())) } }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("输入 / 输出", Icons.Outlined.FolderOpen)

        PathRow("Minecraft 存档目录", "选择包含 level.dat 的文件夹...", state.input, { onStateChange(state.copy(input = it)) }) {
            dirPicker.launch()
        }
        PathRow("输出 JSON 文件", "选择保存位置...", state.output, { onStateChange(state.copy(output = it)) }) {
            fileSaver.launch(suggestedName = "extractions", defaultExtension = "json")
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        SectionTitle("提取选项", Icons.Outlined.Tune)

        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            ModeRadio(RunMode.Region.label, state.mode == RunMode.Region) { onStateChange(state.copy(mode = RunMode.Region)) }
            ModeRadio(RunMode.Datapack.label, state.mode == RunMode.Datapack) { onStateChange(state.copy(mode = RunMode.Datapack)) }
        }

        TextSwitch(
            checked = state.disableFilter,
            onCheckedChange = { onStateChange(state.copy(disableFilter = it)) },
            text = "提取所有文本（禁用内置过滤器）",
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        SectionTitle("自定义过滤规则（可选）", Icons.Outlined.FilterList)

        AnimatedContent(
            targetState = state.mode,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
            label = "mode-filters"
        ) { mode ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (mode == RunMode.Region) {
                    PathRow(
                        "Region 过滤规则 JSON",
                        "留空则使用内置规则...",
                        state.regionPatternPath, { onStateChange(state.copy(regionPatternPath = it)) }
                    ) { patternPicker.launch() }
                } else {
                    PathRow(
                        "MCFunction 过滤规则 JSON",
                        "留空则使用内置规则...",
                        state.mcfPatternPath, { onStateChange(state.copy(mcfPatternPath = it)) }
                    ) { mcfPatternPicker.launch() }
                    PathRow(
                        "MCJson 过滤规则 JSON",
                        "留空则使用内置规则...",
                        state.mcjPatternPath, { onStateChange(state.copy(mcjPatternPath = it)) }
                    ) { mcjPatternPicker.launch() }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        ActionButton("开始提取", isRunning, onRun, enabled = state.input.isNotBlank() && state.output.isNotBlank())
    }
}

// ── 标签页 ②：AI 翻译 ─────────────────────────────────────────

@Composable
fun TranslatePanel(
    state: TranslateState,
    onStateChange: (TranslateState) -> Unit,
    translationProgress: Float,
    translationStatus: String,
    isRunning: Boolean,
    onRun: () -> Unit,
    onSaveSettings: () -> Unit,
    onOptimizePrompt: suspend (String) -> String? = { _ -> null },
) {
    var showToken by remember { mutableStateOf(false) }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var optimizeJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    val inputPicker = rememberFilePickerLauncher(
        type = FileKitType.File(), mode = FileKitMode.Single
    ) { file: PlatformFile? -> file?.let { onStateChange(state.copy(input = it.absolutePath())) } }

    val outputSaver = rememberFileSaverLauncher(FileKitDialogSettings.createDefault()) { file: PlatformFile? ->
        file?.let { onStateChange(state.copy(output = ensureJsonExt(it.absolutePath()))) }
    }
    val termSaver = rememberFileSaverLauncher(FileKitDialogSettings.createDefault()) { file: PlatformFile? ->
        file?.let { onStateChange(state.copy(termOutput = ensureJsonExt(it.absolutePath()))) }
    }
    val termPicker = rememberFilePickerLauncher(
        type = FileKitType.File(), mode = FileKitMode.Single
    ) { file: PlatformFile? -> file?.let { onStateChange(state.copy(existingTermPath = it.absolutePath())) } }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("输入 / 输出", Icons.Outlined.FolderOpen)

        PathRow("提取结果 JSON（来自步骤①）", "选择 extractions.json...", state.input, { onStateChange(state.copy(input = it)) }) {
            inputPicker.launch()
        }
        PathRow("输出替换Mapping JSON", "选择保存位置...", state.mappingOutput, { onStateChange(state.copy(mappingOutput = it)) }) {
            outputSaver.launch(suggestedName = "mappings", defaultExtension = "json")
        }
        PathRow("输出替换文件 JSON", "选择保存位置...", state.output, { onStateChange(state.copy(output = it)) }) {
            outputSaver.launch(suggestedName = "replacements", defaultExtension = "json")
        }
        PathRow("输出术语表 JSON", "选择保存位置...", state.termOutput, { onStateChange(state.copy(termOutput = it)) }) {
            termSaver.launch(suggestedName = "terms", defaultExtension = "json")
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        SectionTitle("AI API 配置", Icons.Outlined.Settings)
        Text(
            "支持 OpenAI 及所有兼容接口（如 APIHub、OneAPI、LobeHub 等）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        ConfigTextField(
            value = state.apiUrl,
            onValueChange = { onStateChange(state.copy(apiUrl = it)) },
            label = { Text("API 地址") },
            placeholder = { Text("留空使用 OpenAI 官方；或填入 https://api.openai.com/v1/") }
        )

        Box {
            ConfigTextField(
                value = state.model,
                onValueChange = { onStateChange(state.copy(model = it)) },
                label = { Text("模型名称") },
                readOnly = true,
                placeholder = { Text("例如 gpt-4o, gpt-4o-mini, deepseek-chat...") },
                trailingIcon = if (state.availableModels.isNotEmpty()) {
                    {
                        IconButton(onClick = { modelMenuExpanded = true }) {
                            Icon(Icons.Outlined.ArrowDropDown, contentDescription = "选择模型")
                        }
                    }
                } else null,
            )
            if (state.availableModels.isNotEmpty()) {
                DropdownMenu(
                    expanded = modelMenuExpanded,
                    onDismissRequest = { modelMenuExpanded = false }
                ) {
                    state.availableModels.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m) },
                            onClick = {
                                onStateChange(state.copy(model = m))
                                modelMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        ConfigTextField(
            value = state.apiToken,
            onValueChange = { onStateChange(state.copy(apiToken = it)) },
            label = { Text("API 密钥") },
            placeholder = { Text("sk-...") },
            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { showToken = !showToken }) {
                    Text(if (showToken) "隐藏" else "显示")
                }
            }
        )

        TextSwitch(
            checked = state.useStreamApi,
            onCheckedChange = { onStateChange(state.copy(useStreamApi = it)) },
            text = "使用流式API(可以解决持续空行的过多重试, 但是可能导致返回变慢)",
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        SectionTitle("可选设置", Icons.Outlined.MoreHoriz)

        PathRow("已有术语表 JSON（可选）", "留空则从头翻译...", state.existingTermPath, { onStateChange(state.copy(existingTermPath = it)) }) {
            termPicker.launch()
        }

        // 翻译风格自定义
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "自定义翻译风格提示词",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            FilledTonalButton(
                onClick = {
                    if (optimizeJob != null) return@FilledTonalButton
                    optimizeJob = scope.launch {
                        onStateChange(state.copy(isOptimizing = true))
                        try {
                            val improved = onOptimizePrompt(state.literatureStyle)
                            if (improved != null) {
                                onStateChange(state.copy(literatureStyle = improved, isOptimizing = false))
                            } else {
                                onStateChange(state.copy(isOptimizing = false))
                            }
                        } catch (_: Exception) {
                            onStateChange(state.copy(isOptimizing = false))
                        } finally {
                            optimizeJob = null
                        }
                    }
                },
                enabled = !state.isOptimizing,
                shape = RoundedCornerShape(8.dp)
            ) {
                if (state.isOptimizing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                } else {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("AI 优化")
                }
            }
        }
        OutlinedTextField(
            value = state.literatureStyle,
            onValueChange = { onStateChange(state.copy(literatureStyle = it)) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
            minLines = 4,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )

        // 翻译进度
        AnimatedVisibility(visible = isRunning || translationProgress > 0f) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "翻译进度",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "${(translationProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    WaveProgressIndicator(
                        progress = { translationProgress },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                    )
                    if (translationStatus.isNotBlank()) {
                        Text(
                            translationStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onSaveSettings,
                enabled = state.model.isNotBlank() && state.apiToken.isNotBlank(),
                modifier = Modifier.weight(0.35f).height(44.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("保存 API 设置")
            }
            Box(modifier = Modifier.weight(0.65f)) {
                ActionButton(
                    "开始 AI 翻译",
                    isRunning,
                    onRun,
                    enabled = state.input.isNotBlank() && state.output.isNotBlank()
                            && state.termOutput.isNotBlank() && state.model.isNotBlank() && state.apiToken.isNotBlank()
                )
            }
        }
    }
}

// ── 标签页 ③：回填 ────────────────────────────────────────────

@Composable
fun BackfillPanel(
    state: BackfillState,
    onStateChange: (BackfillState) -> Unit,
    isRunning: Boolean,
    onRun: () -> Unit
) {
    val dirPicker = rememberDirectoryPickerLauncher { file: PlatformFile? ->
        file?.let { onStateChange(state.copy(input = it.absolutePath())) }
    }
    val filePicker = rememberFilePickerLauncher(
        type = FileKitType.File(), mode = FileKitMode.Single
    ) { file: PlatformFile? -> file?.let { onStateChange(state.copy(replacements = it.absolutePath())) } }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("输入 / 输出", Icons.Outlined.FolderOpen)

        PathRow("Minecraft 存档目录", "选择包含 level.dat 的文件夹...", state.input, { onStateChange(state.copy(input = it)) }) {
            dirPicker.launch()
        }
        PathRow("替换文件 JSON（来自步骤②）", "选择 replacements.json...", state.replacements, { onStateChange(state.copy(replacements = it)) }) {
            filePicker.launch()
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        SectionTitle("回填模式", Icons.Outlined.Tune)

        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            ModeRadio(RunMode.Region.label, state.mode == RunMode.Region) { onStateChange(state.copy(mode = RunMode.Region)) }
            ModeRadio(RunMode.Datapack.label, state.mode == RunMode.Datapack) { onStateChange(state.copy(mode = RunMode.Datapack)) }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    "回填会直接修改存档文件，建议操作前备份！",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        ActionButton("开始回填", isRunning, onRun, enabled = state.input.isNotBlank() && state.replacements.isNotBlank())
    }
}

// ── 工具函数 ────────────────────────────────────────────────

private fun ensureJsonExt(path: String): String =
    if (path.endsWith(".json", ignoreCase = true)) path else "$path.json"