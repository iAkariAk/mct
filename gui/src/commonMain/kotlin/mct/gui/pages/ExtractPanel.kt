package mct.gui.pages

import androidx.compose.animation.*
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
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import mct.gui.components.*
import mct.gui.model.ExtractState
import mct.gui.model.PatternState
import mct.gui.model.RunMode
import mct.gui.util.ensureJsonExt

@Composable
fun ExtractPanel(
    state: ExtractState,
    onStateChange: (ExtractState) -> Unit,
    isRunning: Boolean,
    onRun: () -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme
    val dirPicker = rememberDirectoryPickerLauncher { file: PlatformFile? ->
        file?.let { onStateChange(state.copy(input = it.absolutePath())) }
    }
    val fileSaver = rememberFileSaverLauncher(FileKitDialogSettings.createDefault()) { file: PlatformFile? ->
        file?.let { onStateChange(state.copy(output = ensureJsonExt(it.absolutePath()))) }
    }

    val patterns = state.patterns
    val updatePatterns: ((PatternState) -> PatternState) -> Unit = { transform ->
        onStateChange(state.copy(patterns = transform(patterns)))
    }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("输入 / 输出", Icons.Outlined.FolderOpen)

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

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        SectionTitle("提取选项", Icons.Outlined.Tune)

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            RunMode.entries.forEach { mode ->
                ModeRadio(mode.label, state.mode == mode) { onStateChange(state.copy(mode = mode)) }
            }
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
            transitionSpec = {
                (fadeIn(animationSpec = motionScheme.defaultEffectsSpec()) togetherWith
                    fadeOut(animationSpec = motionScheme.fastEffectsSpec())).using(
                    SizeTransform(
                        clip = false,
                        sizeAnimationSpec = { _, _ -> motionScheme.defaultSpatialSpec() },
                    )
                )
            },
            label = "mode-filters"
        ) { mode ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (mode) {
                    RunMode.Region -> PatternFileRow(
                        "Region 过滤规则 JSON",
                        patterns.regionPatternPath,
                        { path -> updatePatterns { it.copy(regionPatternPath = path) } },
                    )

                    RunMode.Datapack -> {
                        PatternFileRow(
                            "MCFunction 过滤规则 JSON",
                            patterns.commandPatternPath,
                            { path -> updatePatterns { it.copy(commandPatternPath = path) } },
                        )
                        PatternFileRow(
                            "Command Data 过滤规则 JSON",
                            patterns.commandDataPatternPath,
                            { path -> updatePatterns { it.copy(commandDataPatternPath = path) } },
                        )
                        PatternFileRow(
                            "MCJson 过滤规则 JSON",
                            patterns.mcjPatternPath,
                            { path -> updatePatterns { it.copy(mcjPatternPath = path) } },
                        )
                        PatternFileRow(
                            "Command 正则提取规则 JSON",
                            patterns.commandRegexPatternPath,
                            { path -> updatePatterns { it.copy(commandRegexPatternPath = path) } },
                            placeholder = "留空则不使用...",
                        )
                    }

                    RunMode.Cext -> PatternFileRow(
                        "Cext 规则 JSON",
                        patterns.cextPatternPath,
                        { path -> updatePatterns { it.copy(cextPatternPath = path) } },
                        placeholder = "必填，规则需包含 select 与 kind",
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        ActionButton("开始提取", isRunning, onRun, enabled = state.input.isNotBlank() && state.output.isNotBlank())
    }
}
