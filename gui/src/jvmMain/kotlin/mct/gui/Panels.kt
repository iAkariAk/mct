package mct.gui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

// ── 标签页 ①：提取 ────────────────────────────────────────────

@Composable
fun ExtractPanel(
    inputPath: String, onInputChange: (String) -> Unit,
    outputPath: String, onOutputChange: (String) -> Unit,
    mode: String, onModeChange: (String) -> Unit,
    disableFilter: Boolean, onDisableFilterChange: (Boolean) -> Unit,
    isRunning: Boolean, onRun: () -> Unit
) {
    val dirPicker = rememberDirectoryPickerLauncher { file: PlatformFile? ->
        file?.let { onInputChange(it.absolutePath()) }
    }
    val fileSaver = rememberFileSaverLauncher(FileKitDialogSettings.createDefault()) { file: PlatformFile? ->
        file?.let { onOutputChange(ensureJsonExt(it.absolutePath())) }
    }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("输入 / 输出", Icons.Outlined.FolderOpen)

        PathRow("Minecraft 存档目录", "选择包含 level.dat 的文件夹...", inputPath, onInputChange) {
            dirPicker.launch()
        }
        PathRow("输出 JSON 文件", "选择保存位置...", outputPath, onOutputChange) {
            fileSaver.launch(suggestedName = "extractions", extension = "json")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)

        SectionTitle("提取选项", Icons.Outlined.Tune)

        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            ModeRadio("Region (.mca 区域文件)", mode == "region") { onModeChange("region") }
            ModeRadio("Datapack (数据包)", mode == "datapack") { onModeChange("datapack") }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = disableFilter, onCheckedChange = onDisableFilterChange)
            Text(
                "提取所有文本（禁用内置过滤器）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(Modifier.height(4.dp))

        ActionButton("开始提取", isRunning, onRun, enabled = inputPath.isNotBlank() && outputPath.isNotBlank())
    }
}

// ── 标签页 ②：AI 翻译 ─────────────────────────────────────────

@Composable
fun TranslatePanel(
    inputPath: String, onInputChange: (String) -> Unit,
    outputPath: String, onOutputChange: (String) -> Unit,
    termOutput: String, onTermOutputChange: (String) -> Unit,
    apiUrl: String, onApiUrlChange: (String) -> Unit,
    apiToken: String, onApiTokenChange: (String) -> Unit,
    model: String, onModelChange: (String) -> Unit,
    existingTermPath: String, onExistingTermPathChange: (String) -> Unit,
    onSaveSettings: () -> Unit,
    translationProgress: Float, translationStatus: String,
    isRunning: Boolean, onRun: () -> Unit
) {
    var showToken by remember { mutableStateOf(false) }

    val inputPicker = rememberFilePickerLauncher(
        type = FileKitType.File(),
        mode = FileKitMode.Single
    ) { file: PlatformFile? -> file?.let { onInputChange(it.absolutePath()) } }

    val outputSaver = rememberFileSaverLauncher(FileKitDialogSettings.createDefault()) { file: PlatformFile? ->
        file?.let { onOutputChange(ensureJsonExt(it.absolutePath())) }
    }
    val termSaver = rememberFileSaverLauncher(FileKitDialogSettings.createDefault()) { file: PlatformFile? ->
        file?.let { onTermOutputChange(ensureJsonExt(it.absolutePath())) }
    }
    val termPicker = rememberFilePickerLauncher(
        type = FileKitType.File(),
        mode = FileKitMode.Single
    ) { file: PlatformFile? -> file?.let { onExistingTermPathChange(it.absolutePath()) } }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("输入 / 输出", Icons.Outlined.FolderOpen)

        PathRow("提取结果 JSON（来自步骤①）", "选择 extractions.json...", inputPath, onInputChange) {
            inputPicker.launch()
        }
        PathRow("输出替换文件 JSON", "选择保存位置...", outputPath, onOutputChange) {
            outputSaver.launch(suggestedName = "replacements", extension = "json")
        }
        PathRow("输出术语表 JSON", "选择保存位置...", termOutput, onTermOutputChange) {
            termSaver.launch(suggestedName = "terms", extension = "json")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)

        SectionTitle("AI API 配置", Icons.Outlined.Settings)
        Text(
            "支持 OpenAI 及所有兼容接口（如 APIHub、OneAPI、LobeHub 等）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = apiUrl,
            onValueChange = onApiUrlChange,
            label = { Text("API 地址") },
            placeholder = { Text("留空使用 OpenAI 官方；或填入 https://api.openai.com/v1") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )

        OutlinedTextField(
            value = model,
            onValueChange = onModelChange,
            label = { Text("模型名称") },
            placeholder = { Text("例如 gpt-4o, gpt-4o-mini, deepseek-chat...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )

        OutlinedTextField(
            value = apiToken,
            onValueChange = onApiTokenChange,
            label = { Text("API 密钥") },
            placeholder = { Text("sk-...") },
            singleLine = true,
            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { showToken = !showToken }) {
                    Text(if (showToken) "隐藏" else "显示")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)

        SectionTitle("可选设置", Icons.Outlined.MoreHoriz)

        PathRow("已有术语表 JSON（可选）", "留空则从头翻译...", existingTermPath, onExistingTermPathChange) {
            termPicker.launch()
        }

        // 翻译进度
        AnimatedVisibility(visible = isRunning || translationProgress > 0f) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
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
                    LinearProgressIndicator(
                        progress = { translationProgress },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
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
                enabled = model.isNotBlank() && apiToken.isNotBlank(),
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
                    enabled = inputPath.isNotBlank() && outputPath.isNotBlank()
                            && termOutput.isNotBlank() && model.isNotBlank() && apiToken.isNotBlank()
                )
            }
        }
    }
}

// ── 标签页 ③：回填 ────────────────────────────────────────────

@Composable
fun BackfillPanel(
    inputPath: String, onInputChange: (String) -> Unit,
    replacementsPath: String, onReplacementsChange: (String) -> Unit,
    mode: String, onModeChange: (String) -> Unit,
    isRunning: Boolean, onRun: () -> Unit
) {
    val dirPicker = rememberDirectoryPickerLauncher { file: PlatformFile? ->
        file?.let { onInputChange(it.absolutePath()) }
    }
    val filePicker = rememberFilePickerLauncher(
        type = FileKitType.File(),
        mode = FileKitMode.Single
    ) { file: PlatformFile? -> file?.let { onReplacementsChange(it.absolutePath()) } }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("输入 / 输出", Icons.Outlined.FolderOpen)

        PathRow("Minecraft 存档目录", "选择包含 level.dat 的文件夹...", inputPath, onInputChange) {
            dirPicker.launch()
        }
        PathRow("替换文件 JSON（来自步骤②）", "选择 replacements.json...", replacementsPath, onReplacementsChange) {
            filePicker.launch()
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)

        SectionTitle("回填模式", Icons.Outlined.Tune)

        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            ModeRadio("Region (.mca 区域文件)", mode == "region") { onModeChange("region") }
            ModeRadio("Datapack (数据包)", mode == "datapack") { onModeChange("datapack") }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            )
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

        ActionButton(
            "开始回填",
            isRunning,
            onRun,
            enabled = inputPath.isNotBlank() && replacementsPath.isNotBlank()
        )
    }
}

// ── 工具函数 ────────────────────────────────────────────────

private fun ensureJsonExt(path: String): String =
    if (path.endsWith(".json", ignoreCase = true)) path else "$path.json"