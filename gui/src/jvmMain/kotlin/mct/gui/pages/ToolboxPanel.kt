package mct.gui.pages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import mct.gui.components.*
import mct.gui.model.PointerKind
import mct.gui.model.ToolboxState

@Composable
fun ToolboxPanel(
    state: ToolboxState,
    onStateChange: (ToolboxState) -> Unit,
    isRunning: Boolean,
    onRunPointerTest: () -> Unit,
    onRunExportSnbt: () -> Unit,
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
                            state.pointerResult!!,
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
        PathRow("Minecraft 存档目录", "选择包含 level.dat 的文件夹...", state.exportInput, { onStateChange(state.copy(exportInput = it)) }) {
            dirPicker.launch()
        }
        PathRow("SNBT 导出目录", "选择保存位置...", state.exportOutput, { onStateChange(state.copy(exportOutput = it)) }) {
            outputDirPicker.launch()
        }

        Spacer(Modifier.height(4.dp))

        ActionButton("导出 SNBT", isRunning, onRunExportSnbt, enabled = state.exportInput.isNotBlank() && state.exportOutput.isNotBlank())
    }
}
