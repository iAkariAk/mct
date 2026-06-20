package mct.gui.pages

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import mct.gui.components.*
import mct.gui.model.ExtractState
import mct.gui.model.RunMode

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
    val mcfDataPatternPicker = rememberFilePickerLauncher(
        type = FileKitType.File(), mode = FileKitMode.Single
    ) { file: PlatformFile? -> file?.let { onStateChange(state.copy(mcfDataPatternPath = it.absolutePath())) } }
    val mcjPatternPicker = rememberFilePickerLauncher(
        type = FileKitType.File(), mode = FileKitMode.Single
    ) { file: PlatformFile? -> file?.let { onStateChange(state.copy(mcjPatternPath = it.absolutePath())) } }
    val mcfunctionRegexPatternPicker = rememberFilePickerLauncher(
        type = FileKitType.File(), mode = FileKitMode.Single
    ) { file: PlatformFile? -> file?.let { onStateChange(state.copy(mcfunctionRegexPatternPath = it.absolutePath())) } }

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
                        "MCFunction Data 过滤规则 JSON",
                        "留空则使用内置规则...",
                        state.mcfDataPatternPath, { onStateChange(state.copy(mcfDataPatternPath = it)) }
                    ) { mcfDataPatternPicker.launch() }
                    PathRow(
                        "MCJson 过滤规则 JSON",
                        "留空则使用内置规则...",
                        state.mcjPatternPath, { onStateChange(state.copy(mcjPatternPath = it)) }
                    ) { mcjPatternPicker.launch() }
                    PathRow(
                        "MCFunction 正则提取规则 JSON",
                        "留空则不使用...",
                        state.mcfunctionRegexPatternPath, { onStateChange(state.copy(mcfunctionRegexPatternPath = it)) }
                    ) { mcfunctionRegexPatternPicker.launch() }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        ActionButton("开始提取", isRunning, onRun, enabled = state.input.isNotBlank() && state.output.isNotBlank())
    }
}

private fun ensureJsonExt(path: String): String =
    if (path.endsWith(".json", ignoreCase = true)) path else "$path.json"
