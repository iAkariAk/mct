package mct.gui.pages

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import mct.gui.components.ActionButton
import mct.gui.components.ModeRadio
import mct.gui.components.PathRow
import mct.gui.components.SectionTitle
import mct.gui.model.BackfillState
import mct.gui.model.RunMode

@Composable
fun BackfillPanel(
    state: BackfillState,
    onStateChange: (BackfillState) -> Unit,
    isRunning: Boolean,
    onRun: () -> Unit,
) {
    val dirPicker = rememberDirectoryPickerLauncher { file: PlatformFile? ->
        file?.let { onStateChange(state.copy(input = it.absolutePath())) }
    }
    val filePicker = rememberFilePickerLauncher(
        type = FileKitType.File(), mode = FileKitMode.Single
    ) { file: PlatformFile? -> file?.let { onStateChange(state.copy(replacements = it.absolutePath())) } }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("输入 / 输出", Icons.Outlined.FolderOpen)

        PathRow(
            "Minecraft 存档目录",
            "选择包含 level.dat 的文件夹...",
            state.input,
            { onStateChange(state.copy(input = it)) }) {
            dirPicker.launch()
        }
        PathRow(
            "替换文件 JSON（来自步骤②）",
            "选择 replacements.json...",
            state.replacements,
            { onStateChange(state.copy(replacements = it)) }) {
            filePicker.launch()
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        SectionTitle("回填模式", Icons.Outlined.Tune)

        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            ModeRadio(
                RunMode.Region.label,
                state.mode == RunMode.Region
            ) { onStateChange(state.copy(mode = RunMode.Region)) }
            ModeRadio(
                RunMode.Datapack.label,
                state.mode == RunMode.Datapack
            ) { onStateChange(state.copy(mode = RunMode.Datapack)) }
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

        ActionButton(
            "开始回填",
            isRunning,
            onRun,
            enabled = state.input.isNotBlank() && state.replacements.isNotBlank()
        )
    }
}
