package mct.gui.pages

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import mct.gui.components.*
import mct.gui.model.ExtractState
import mct.gui.model.MCTPatternState
import mct.gui.model.RunMode
import mct.gui.util.ensureJsonExt

@Composable
fun ExtractPanel(
    state: ExtractState,
    onStateChange: (ExtractState) -> Unit,
    isRunning: Boolean,
    onRun: () -> Unit,
) {
    // Read the latest state at invocation time: capturing `state` would give every field
    // callback a new identity on each edit and defeat memoisation downstream.
    val currentState by rememberUpdatedState(state)
    // Stable identity, or the rule editor's remembered callbacks restart on every keystroke.
    val onPatternsChange: (MCTPatternState) -> Unit = remember(onStateChange) {
        { patterns -> onStateChange(currentState.copy(patterns = patterns)) }
    }
    val dirPicker = rememberDirectoryPickerLauncher { file: PlatformFile? ->
        file?.let { onStateChange(currentState.copy(input = it.absolutePath())) }
    }
    val fileSaver = rememberFileSaverLauncher(FileKitDialogSettings.createDefault()) { file: PlatformFile? ->
        file?.let { onStateChange(currentState.copy(output = ensureJsonExt(it.absolutePath()))) }
    }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PanelSection("输入 / 输出", Icons.Outlined.FolderOpen) {
            PathRow(
                "Minecraft 存档目录",
                "选择包含 level.dat 的文件夹...",
                state.input,
                { onStateChange(currentState.copy(input = it)) }) {
                dirPicker.launch()
            }
            PathRow("输出 JSON 文件", "选择保存位置...", state.output, { onStateChange(currentState.copy(output = it)) }) {
                fileSaver.launch(suggestedName = "extractions", defaultExtension = "json")
            }
        }

        PanelSection("提取选项", Icons.Outlined.Tune) {
            EnumButtonGroup(
                entries = RunMode.entries,
                selected = state.mode,
                label = { it.label },
                onSelected = { onStateChange(currentState.copy(mode = it)) },
            )
            Text(
                state.mode.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        MCTPatternEditor(
            patterns = state.patterns,
            onPatternsChange = onPatternsChange,
            slots = state.mode.patternSlots,
        )

        Spacer(Modifier.height(4.dp))

        ActionButton("开始提取", isRunning, onRun, enabled = state.input.isNotBlank() && state.output.isNotBlank())
    }
}
