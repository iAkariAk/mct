package mct.gui.pages

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Rule
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
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import mct.gui.components.*
import mct.gui.model.*
import mct.gui.util.ensureExtension

/**
 * 补丁工作台：把翻译映射固化成可分发、可校验的补丁，或把补丁应用到目标存档。
 */
@Composable
fun PatchPanel(
    state: PatchState,
    onStateChange: (PatchState) -> Unit,
    isRunning: Boolean,
    onCreate: () -> Unit,
    onApply: () -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme
    val create = state.create
    val apply = state.apply

    val createDirPicker = rememberDirectoryPickerLauncher { file: PlatformFile? ->
        file?.let { onStateChange(state.copy(create = create.copy(input = it.absolutePath()))) }
    }
    val mappingPicker = rememberFilePickerLauncher(
        type = FileKitType.File(), mode = FileKitMode.Single,
    ) { file: PlatformFile? ->
        file?.let { onStateChange(state.copy(create = create.copy(mapping = it.absolutePath()))) }
    }
    val patchSaver = rememberFileSaverLauncher(FileKitDialogSettings.createDefault()) { file: PlatformFile? ->
        file?.let {
            onStateChange(
                state.copy(create = create.copy(output = ensureExtension(it.absolutePath(), create.format.extension)))
            )
        }
    }
    val applyDirPicker = rememberDirectoryPickerLauncher { file: PlatformFile? ->
        file?.let { onStateChange(state.copy(apply = apply.copy(input = it.absolutePath()))) }
    }
    val patchPicker = rememberFilePickerLauncher(
        type = FileKitType.File(), mode = FileKitMode.Single,
    ) { file: PlatformFile? ->
        file?.let { onStateChange(state.copy(apply = apply.copy(patch = it.absolutePath()))) }
    }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EnumSegmentedButtons(
            entries = PatchSection.entries,
            selected = state.section,
            label = { it.label },
            onSelected = { onStateChange(state.copy(section = it)) },
        )

        AnimatedContent(
            targetState = state.section,
            transitionSpec = {
                (fadeIn(animationSpec = motionScheme.defaultEffectsSpec()) togetherWith
                    fadeOut(animationSpec = motionScheme.fastEffectsSpec())).using(
                    SizeTransform(
                        clip = false,
                        sizeAnimationSpec = { _, _ -> motionScheme.defaultSpatialSpec() },
                    )
                )
            },
            label = "patch-section",
        ) { section ->
            when (section) {
                PatchSection.Create -> PatchCreateSection(
                    state = create,
                    onStateChange = { onStateChange(state.copy(create = it)) },
                    isRunning = isRunning,
                    onBrowseInput = { createDirPicker.launch() },
                    onBrowseMapping = { mappingPicker.launch() },
                    onBrowseOutput = { patchSaver.launch(suggestedName = "patch", defaultExtension = create.format.extension) },
                    onCreate = onCreate,
                )

                PatchSection.Apply -> PatchApplySection(
                    state = apply,
                    onStateChange = { onStateChange(state.copy(apply = it)) },
                    isRunning = isRunning,
                    onBrowseInput = { applyDirPicker.launch() },
                    onBrowsePatch = { patchPicker.launch() },
                    onApply = onApply,
                )
            }
        }
    }
}

@Composable
private fun PatchCreateSection(
    state: PatchCreateState,
    onStateChange: (PatchCreateState) -> Unit,
    isRunning: Boolean,
    onBrowseInput: () -> Unit,
    onBrowseMapping: () -> Unit,
    onBrowseOutput: () -> Unit,
    onCreate: () -> Unit,
) {
    val patterns = state.patterns
    val updatePatterns: ((PatternState) -> PatternState) -> Unit = { transform ->
        onStateChange(state.copy(patterns = transform(patterns)))
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("输入 / 输出", Icons.Outlined.FolderOpen)

        PathRow(
            "Minecraft 存档目录",
            "选择包含 level.dat 的文件夹...",
            state.input,
            { onStateChange(state.copy(input = it)) },
            onBrowse = onBrowseInput,
        )
        PathRow(
            "翻译映射 JSON",
            "选择 mappings.json...",
            state.mapping,
            { onStateChange(state.copy(mapping = it)) },
            onBrowse = onBrowseMapping,
        )
        PathRow(
            "补丁输出文件",
            "选择保存位置...",
            state.output,
            { onStateChange(state.copy(output = it)) },
            onBrowse = onBrowseOutput,
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        SectionTitle("补丁选项", Icons.Outlined.Tune)

        Text(
            "求值方式",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        EnumSegmentedButtons(
            entries = PatchKind.entries,
            selected = state.kind,
            label = { it.label },
            onSelected = { onStateChange(state.copy(kind = it)) },
        )

        Text(
            "文件格式",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        EnumSegmentedButtons(
            entries = PatchFormat.entries,
            selected = state.format,
            label = { it.label },
            onSelected = { onStateChange(state.copy(format = it)) },
        )

        TextSwitch(
            checked = state.validation,
            onCheckedChange = { onStateChange(state.copy(validation = it)) },
            text = "记录存档校验信息（应用时检测内容变化）",
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        SectionTitle("自定义规则（可选）", Icons.AutoMirrored.Outlined.Rule)

        PatternFileRow(
            "Region 过滤规则 JSON",
            patterns.regionPatternPath,
            { path -> updatePatterns { it.copy(regionPatternPath = path) } },
        )
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
        PatternFileRow(
            "Cext 规则 JSON",
            patterns.cextPatternPath,
            { path -> updatePatterns { it.copy(cextPatternPath = path) } },
            placeholder = "留空则不使用...",
        )

        Spacer(Modifier.height(4.dp))

        ActionButton(
            "创建补丁",
            isRunning,
            onCreate,
            enabled = state.input.isNotBlank() && state.mapping.isNotBlank() && state.output.isNotBlank(),
        )
    }
}

@Composable
private fun PatchApplySection(
    state: PatchApplyState,
    onStateChange: (PatchApplyState) -> Unit,
    isRunning: Boolean,
    onBrowseInput: () -> Unit,
    onBrowsePatch: () -> Unit,
    onApply: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("输入", Icons.Outlined.FolderOpen)

        PathRow(
            "Minecraft 存档目录",
            "选择包含 level.dat 的文件夹...",
            state.input,
            { onStateChange(state.copy(input = it)) },
            onBrowse = onBrowseInput,
        )
        PathRow(
            "补丁文件",
            "选择 .json 或 .mctp 补丁...",
            state.patch,
            { onStateChange(state.copy(patch = it)) },
            onBrowse = onBrowsePatch,
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        SectionTitle("应用选项", Icons.Outlined.Tune)

        Text(
            "文件格式",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        EnumSegmentedButtons(
            entries = PatchFormat.entries,
            selected = state.format,
            label = { it.label },
            onSelected = { onStateChange(state.copy(format = it)) },
        )

        Text(
            "校验不一致时",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        EnumSegmentedButtons(
            entries = PatchStrategy.entries,
            selected = state.strategy,
            label = { it.label },
            onSelected = { onStateChange(state.copy(strategy = it)) },
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            ),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    "应用补丁会直接修改存档文件，建议操作前备份！",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        ActionButton(
            "应用补丁",
            isRunning,
            onApply,
            enabled = state.input.isNotBlank() && state.patch.isNotBlank(),
        )
    }
}
