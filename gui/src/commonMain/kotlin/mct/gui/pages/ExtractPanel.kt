package mct.gui.pages

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import mct.gui.components.*
import mct.gui.model.ExtractState
import mct.gui.model.RunMode
import mct.gui.util.ensureJsonExt

@Composable
fun ExtractPanel(
    state: ExtractState,
    onStateChange: (ExtractState) -> Unit,
    isRunning: Boolean,
    onRun: () -> Unit,
) {
    val dirPicker = rememberDirectoryPickerLauncher { file: PlatformFile? ->
        file?.let { onStateChange(state.copy(input = it.absolutePath())) }
    }
    val fileSaver = rememberFileSaverLauncher(FileKitDialogSettings.createDefault()) { file: PlatformFile? ->
        file?.let { onStateChange(state.copy(output = ensureJsonExt(it.absolutePath()))) }
    }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PanelSection("输入 / 输出", Icons.Outlined.FolderOpen) {
            PathRow(
                "Minecraft 存档目录",
                "选择包含 level.dat 的文件夹...",
                state.input,
                { onStateChange(state.copy(input = it)) }) {
                dirPicker.launch()
            }
            PathRow("输出 JSON 文件", "选择保存位置...", state.output, { onStateChange(state.copy(output = it)) }) {
                fileSaver.launch(suggestedName = "extractions", defaultExtension = "json")
            }
        }

        PanelSection("提取选项", Icons.Outlined.Tune) {
            EnumButtonGroup(
                entries = RunMode.entries,
                selected = state.mode,
                label = { it.label },
                onSelected = { onStateChange(state.copy(mode = it)) },
            )
            Text(
                state.mode.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        MCTPatternEditor(
            patterns = state.patterns,
            onPatternsChange = { onStateChange(state.copy(patterns = it)) },
            slots = state.mode.patternSlots,
        )

        Spacer(Modifier.height(4.dp))

        ActionButton("开始提取", isRunning, onRun, enabled = state.input.isNotBlank() && state.output.isNotBlank())
    }
}
