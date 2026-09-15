package mct.gui.pages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TextSnippet
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import mct.gui.components.*
import mct.gui.model.TermExtractState
import mct.gui.util.ensureJsonExt

@Composable
fun TermExtractPanel(
    state: TermExtractState,
    onStateChange: (TermExtractState) -> Unit,
    isRunning: Boolean,
    onRun: () -> Unit,
    onCancel: () -> Unit = {},
) {
    // Latest state at invocation time keeps every field callback's identity stable.
    val currentState by rememberUpdatedState(state)
    val inputPicker = rememberFilePickerLauncher(
        type = FileKitType.File(), mode = FileKitMode.Single
    ) { file: PlatformFile? -> file?.let { onStateChange(currentState.copy(input = it.absolutePath())) } }

    val outputSaver = rememberFileSaverLauncher(FileKitDialogSettings.createDefault()) { file: PlatformFile? ->
        file?.let { onStateChange(currentState.copy(output = ensureJsonExt(it.absolutePath()))) }
    }

    val termPicker = rememberFilePickerLauncher(
        type = FileKitType.File(), mode = FileKitMode.Single
    ) { file: PlatformFile? -> file?.let { onStateChange(currentState.copy(existingTermPath = it.absolutePath())) } }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ── Header ───────────────────────────────────────────────
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.tertiary,
                )
                Text(
                    "从提取结果中自动识别人名、地名和专有名词，生成术语表供翻译时参考。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ── Input / output ───────────────────────────────────────
        SectionTitle("输入 / 输出", Icons.Outlined.FolderOpen)

        PathRow(
            label = "提取结果 JSON（来自步骤①）",
            placeholder = "选择 extractions.json...",
            value = state.input,
            onValueChange = { onStateChange(currentState.copy(input = it)) },
            onBrowse = { inputPicker.launch() },
        )
        PathRow(
            label = "输出术语表 JSON",
            placeholder = "选择保存位置...",
            value = state.output,
            onValueChange = { onStateChange(currentState.copy(output = it)) },
            mustExist = false,
            onBrowse = { outputSaver.launch(suggestedName = "terms", defaultExtension = "json") },
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        // ── Extraction options ───────────────────────────────────
        SectionTitle("提取选项", Icons.AutoMirrored.Outlined.TextSnippet)

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Target language
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Outlined.Language,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "目标语言",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                OutlinedTextField(
                    value = state.targetLanguage,
                    onValueChange = { onStateChange(currentState.copy(targetLanguage = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    placeholder = { Text("简体中文") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                )

                Text(
                    "术语将被翻译为此语言。默认为简体中文。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )

                LiteratureStyleField(
                    value = state.literatureStyle,
                    onValueChange = { onStateChange(currentState.copy(literatureStyle = it)) },
                    title = "自定义术语提取风格提示词",
                )

                MapInfoFields(
                    value = state.mapInfo,
                    onValueChange = { onStateChange(currentState.copy(mapInfo = it)) },
                )

                ExtraPromptsField(
                    value = state.extraPrompts,
                    onValueChange = { onStateChange(currentState.copy(extraPrompts = it)) },
                )

                // Existing term table
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Outlined.Bookmark,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                    Text(
                        "已有术语表（可选）",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                PathRow(
                    label = "已有术语表 JSON（增量提取）",
                    placeholder = "留空则从头提取...",
                    value = state.existingTermPath,
                    onValueChange = { onStateChange(currentState.copy(existingTermPath = it)) },
                    onBrowse = { termPicker.launch() },
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // ── Actions ──────────────────────────────────────────────
        ActionButton(
            label = "开始术语提取",
            running = isRunning,
            onClick = onRun,
            enabled = state.input.isNotBlank() && state.output.isNotBlank(),
            onCancel = if (isRunning) onCancel else null,
        )
    }
}
