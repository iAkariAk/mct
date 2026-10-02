@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package mct.gui.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.automirrored.outlined.MergeType
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.yuroyami.kiteimage.compose.KiteImage
import mct.gui.components.*
import mct.gui.model.*
import mct.gui.platform.platformPathOf
import mct.gui.services.mapBitmap
import mct.gui.services.mapSummary
import mct.gui.state.MapToolController
import mct.gui.util.fileNameOf
import mct.gui.util.joinPath
import mct.map.MapFile

/**
 * One toolbox function as a page of the function area.
 *
 * Every tool used to be a modal dialog; it is the same form and the same run, but the area switches
 * to it instead of covering the catalogue. Nothing about a tool is re-implemented — each one hands
 * its fields to the same `mct.gui.services` function as before.
 *
 * The page is laid out as a header, a scrolling body and a pinned action bar, so the run button
 * stays reachable while the form is longer than the window. Back is never blocked: a run belongs to
 * the services and the view model's state, not to this page, so leaving it does not lose anything.
 */
@Composable
fun ToolboxOperationPage(
    operation: ToolboxOperation,
    state: ToolboxState,
    isRunning: Boolean,
    onStateChange: (ToolboxState) -> Unit,
    onBack: () -> Unit,
    onRun: () -> Unit,
    onCancel: () -> Unit,
    mapController: MapToolController,
    modifier: Modifier = Modifier,
) {
    // Read the latest state at invocation time: the launchers below are remembered, so a callback
    // capturing `state` directly would write a snapshot from before the user's last edit.
    val currentState by rememberUpdatedState(state)
    val update: ((ToolboxState) -> ToolboxState) -> Unit = remember(onStateChange) {
        { transform -> onStateChange(transform(currentState)) }
    }

    Column(modifier.fillMaxSize()) {
        FunctionPageHeader(
            title = operation.title,
            icon = operation.icon(),
            supporting = operation.summary(),
            onBack = onBack,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (operation) {
                ToolboxOperation.PointerTest -> PointerTestFields(state, update)
                ToolboxOperation.ComponentPreview -> ComponentPreviewFields(state, update)
                ToolboxOperation.PatternInspect -> PatternInspectFields(state, update)
                ToolboxOperation.ExportSnbt -> ExportSnbtFields(state, update)
                ToolboxOperation.FlattenPool -> PoolFields(state, update, showMapping = false)
                ToolboxOperation.UnflattenPool -> PoolFields(state, update, showMapping = true)
                ToolboxOperation.GenerateMtlx -> GenerateMtlxFields(state, update)
                ToolboxOperation.TranslateMtlx -> TranslateMtlxFields(state, update)
                ToolboxOperation.ReplaceAll -> ReplaceAllFields(state, update)
                ToolboxOperation.ExportSchema -> ExportSchemaFields(state, update)
                ToolboxOperation.CommandTest -> CommandTestFields(state, update)
                ToolboxOperation.Convert -> ConvertFields(state, update)
                ToolboxOperation.MapFile -> MapFileFields(state, update, isRunning, mapController)
                ToolboxOperation.DownloadOfficialLanguage -> DownloadOfficialLanguageFields(state, update)
                ToolboxOperation.CombineOfficialLanguage -> CombineOfficialLanguageFields(state, update)
            }
        }
        if (operation.needsRun) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ActionButton(
                label = operation.actionLabel,
                running = isRunning,
                onClick = onRun,
                enabled = operation.isReady(state),
                onCancel = if (isRunning) onCancel else null,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

/** Tools whose content is derived live from their fields have nothing to submit. */
private val ToolboxOperation.needsRun: Boolean
    get() = this != ToolboxOperation.ComponentPreview

private typealias Update = ((ToolboxState) -> ToolboxState) -> Unit

@Composable
private fun ToolboxOperation.icon(): ImageVector = when (this) {
    ToolboxOperation.PointerTest -> Icons.Outlined.GpsFixed
    ToolboxOperation.ComponentPreview -> Icons.Outlined.FormatColorText
    ToolboxOperation.PatternInspect -> Icons.AutoMirrored.Outlined.Rule
    ToolboxOperation.ExportSnbt -> Icons.Outlined.DataObject
    ToolboxOperation.FlattenPool -> Icons.Outlined.AccountTree
    ToolboxOperation.UnflattenPool -> Icons.AutoMirrored.Outlined.MergeType
    ToolboxOperation.GenerateMtlx -> Icons.Outlined.Description
    ToolboxOperation.TranslateMtlx -> Icons.Outlined.Translate
    ToolboxOperation.ReplaceAll -> Icons.Outlined.FindReplace
    ToolboxOperation.ExportSchema -> Icons.Outlined.Schema
    ToolboxOperation.CommandTest -> Icons.Outlined.Terminal
    ToolboxOperation.DownloadOfficialLanguage -> Icons.Outlined.Download
    ToolboxOperation.CombineOfficialLanguage -> Icons.AutoMirrored.Outlined.CompareArrows
    ToolboxOperation.Convert -> Icons.Outlined.SwapHoriz
    ToolboxOperation.MapFile -> Icons.Outlined.Map
}

/** What the catalogue says about this tool; the page header repeats it instead of inventing one. */
private fun ToolboxOperation.summary(): String = ToolboxCards.getValue(this).supporting

private fun ToolboxOperation.isReady(state: ToolboxState): Boolean = when (this) {
    ToolboxOperation.PointerTest -> state.pointerInput.isNotBlank()
    // Never queried: the preview has no action bar (see [needsRun]); the branch keeps the `when`
    // exhaustive as the enum grows.
    ToolboxOperation.ComponentPreview -> true
    ToolboxOperation.PatternInspect -> true
    ToolboxOperation.ExportSnbt -> state.exportInput.isNotBlank() && state.exportOutput.isNotBlank()
    ToolboxOperation.FlattenPool -> state.poolInput.isNotBlank() && state.poolOutput.isNotBlank()
    ToolboxOperation.UnflattenPool -> state.poolInput.isNotBlank() && state.mappingInput.isNotBlank() && state.poolOutput.isNotBlank()
    ToolboxOperation.GenerateMtlx -> state.poolInput.isNotBlank() && state.poolOutput.isNotBlank()
    ToolboxOperation.TranslateMtlx -> state.mtlxInput.isNotBlank() && state.poolInput.isNotBlank() && state.poolOutput.isNotBlank()
    ToolboxOperation.ReplaceAll -> state.poolInput.isNotBlank() && state.poolOutput.isNotBlank()
    ToolboxOperation.ExportSchema -> state.poolOutput.isNotBlank()
    ToolboxOperation.CommandTest -> state.commandInput.isNotBlank()
    ToolboxOperation.DownloadOfficialLanguage -> state.officialMinecraftVersion.isNotBlank() &&
            state.officialOutput.isNotBlank() && state.officialConcurrency.toIntOrNull() in 1..64

    ToolboxOperation.CombineOfficialLanguage -> state.officialSourceLanguage.isNotBlank() &&
            state.officialTargetLanguage.isNotBlank() && state.poolOutput.isNotBlank()

    // The level is optional; a non-blank one has to be something the CLI's `1..9` restriction accepts.
    ToolboxOperation.Convert -> state.convert.let { convert ->
        val level = convert.compressionLevel.toIntOrNull()
        val levelValid = convert.compressionLevel.isBlank() || (level != null && level in 1..9)
        val pathsValid = if (convert.batch) {
            // `--regex` needs a directory to walk, and there is no output path to infer the format from.
            convert.currentDirectory.isNotBlank() && convert.outputFormat != ConvertFormat.Auto
        } else {
            convert.output.isNotBlank()
        }
        convert.input.isNotBlank() && levelValid && pathsValid
    }

    // Only the load is a form submission; export and overwrite act on the map held by the controller.
    ToolboxOperation.MapFile -> state.map.input.isNotBlank()
}

// ── Rules ─────────────────────────────────────────────────────

@Composable
private fun PointerTestFields(state: ToolboxState, update: Update) {
    val patternPicker = filePicker { path -> update { it.copy(pointerPatternPath = path, pointerResult = null) } }

    EnumButtonGroup(
        entries = PointerKind.entries,
        selected = state.pointerKind,
        label = { it.label },
        onSelected = { kind -> update { it.copy(pointerKind = kind, pointerResult = null) } },
    )
    PathRow(
        label = "自定义规则 JSON（可选）",
        placeholder = "留空则仅使用内置规则",
        value = state.pointerPatternPath,
        onValueChange = { path -> update { it.copy(pointerPatternPath = path, pointerResult = null) } },
        onBrowse = { patternPicker.launch() },
    )
    TextSwitch(
        checked = state.noBuiltin,
        onCheckedChange = { disabled -> update { it.copy(noBuiltin = disabled, pointerResult = null) } },
        text = "禁用内置规则",
    )
    ConfigTextField(
        value = state.pointerInput,
        onValueChange = { text -> update { it.copy(pointerInput = text, pointerResult = null) } },
        label = { Text("DataPointer 字符串") },
        placeholder = { Text("例如 >#display>#Name") },
        singleLine = true,
    )
    ResultCard(visible = state.pointerResult != null) {
        val matched = state.pointerResult == "true"
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = if (matched) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    if (matched) Icons.Outlined.CheckCircle else Icons.Outlined.Cancel,
                    contentDescription = null,
                )
                Text(if (matched) "匹配成功" else "未匹配")
            }
        }
    }
}

@Composable
private fun ComponentPreviewFields(state: ToolboxState, update: Update) {
    Text(
        "粘贴一个文本组件（JSON 或 SNBT），按游戏的方式预览：颜色、加粗、斜体与下划线都会显示出来。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    EnumButtonGroup(
        entries = ComponentFormat.entries,
        selected = state.componentFormat,
        label = { it.label },
        onSelected = { format -> update { it.copy(componentFormat = format) } },
    )
    ConfigTextField(
        value = state.componentInput,
        onValueChange = { text -> update { it.copy(componentInput = text) } },
        label = { Text("文本组件") },
        placeholder = { Text("""例如 {"text":"Hello","color":"gold","bold":true}""") },
        singleLine = false,
        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
    )
    val component = remember(state.componentInput, state.componentFormat) {
        parseTextComponent(state.componentInput, state.componentFormat)
    }
    when {
        state.componentInput.isBlank() -> Unit
        component == null -> Text(
            "无法解析：请检查内容与所选格式是否匹配。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )

        else -> TextComponentPreview(component)
    }
}

@Composable
private fun PatternInspectFields(state: ToolboxState, update: Update) {
    val onPatternsChange: (MCTPatternState) -> Unit = remember(update) {
        // The dump belongs to the rules it was produced from; keeping it across a rule change would
        // present the previous run's result as if it were the current one.
        { patterns -> update { it.copy(patternPatterns = patterns, patternResult = "") } }
    }

    Text(
        "按「提取」的方式合并内置规则与所选规则文件，并显示合并后的结果。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    EnumButtonGroup(
        entries = RunMode.entries,
        selected = state.patternMode,
        label = { it.label },
        onSelected = { mode -> update { it.copy(patternMode = mode, patternResult = "") } },
    )
    MCTPatternEditor(
        patterns = state.patternPatterns,
        onPatternsChange = onPatternsChange,
        slots = state.patternMode.patternSlots,
        title = "提取规则",
    )
    ResultCard(visible = state.patternResult.isNotBlank()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                state.patternResult,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
        }
    }
}

@Composable
private fun CommandTestFields(state: ToolboxState, update: Update) {
    val inputPicker = filePicker { path -> update { it.copy(commandInput = path, commandResult = "") } }
    val onPatternsChange: (MCTPatternState) -> Unit = remember(update) {
        { patterns -> update { it.copy(commandPatterns = patterns, commandResult = "") } }
    }

    PathRow(
        label = "命令样例文件",
        value = state.commandInput,
        onValueChange = { path -> update { it.copy(commandInput = path, commandResult = "") } },
        onBrowse = { inputPicker.launch() },
    )
    MCTPatternEditor(
        patterns = state.commandPatterns,
        onPatternsChange = onPatternsChange,
        slots = listOf(
            MCTPatternSlot.Command,
            MCTPatternSlot.CommandData,
            MCTPatternSlot.CommandComponent,
            MCTPatternSlot.CommandRegex,
        ),
        title = "命令提取规则",
    )
    ResultCard(visible = state.commandResult.isNotBlank()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                state.commandResult,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ExportSchemaFields(state: ToolboxState, update: Update) {
    val outputPicker = poolOutputPicker(state.poolOutput, update)
    Text(
        "选择需要导出的规则结构。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    EnumButtonGroup(
        entries = SchemaKind.entries,
        selected = state.schemaKind,
        label = { it.label },
        onSelected = { kind -> update { it.copy(schemaKind = kind) } },
    )
    PathRow(
        label = "Schema 输出 JSON",
        value = state.poolOutput,
        onValueChange = { path -> update { it.copy(poolOutput = path) } },
        mustExist = false,
        onBrowse = { outputPicker.launch() },
    )
}

// ── Text and pools ────────────────────────────────────────────

@Composable
private fun ExportSnbtFields(state: ToolboxState, update: Update) {
    val inputPicker = directoryPicker { path -> update { it.copy(exportInput = path) } }
    val outputDirPicker = directoryPicker { path -> update { it.copy(exportOutput = path) } }

    Text(
        "将存档内的 Region NBT 导出为可读 SNBT，便于调试和审查。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    PathRow(
        label = "Minecraft 存档目录",
        placeholder = "选择包含 level.dat 的目录",
        value = state.exportInput,
        onValueChange = { path -> update { it.copy(exportInput = path) } },
        onBrowse = { inputPicker.launch() },
    )
    PathRow(
        label = "SNBT 导出目录",
        placeholder = "选择保存位置",
        value = state.exportOutput,
        onValueChange = { path -> update { it.copy(exportOutput = path) } },
        mustExist = false,
        onBrowse = { outputDirPicker.launch() },
    )
}

@Composable
private fun PoolFields(state: ToolboxState, update: Update, showMapping: Boolean) {
    val inputPicker = filePicker { path -> update { it.copy(poolInput = path) } }
    val mappingPicker = filePicker { path -> update { it.copy(mappingInput = path) } }
    val outputPicker = poolOutputPicker(state.poolOutput, update)

    PathRow(
        label = "提取结果 JSON",
        value = state.poolInput,
        onValueChange = { path -> update { it.copy(poolInput = path) } },
        onBrowse = { inputPicker.launch() },
    )
    if (showMapping) PathRow(
        label = "映射 JSON",
        value = state.mappingInput,
        onValueChange = { path -> update { it.copy(mappingInput = path) } },
        onBrowse = { mappingPicker.launch() },
    )
    PathRow(
        label = if (showMapping) "替换输出 JSON" else "文本池输出 JSON",
        value = state.poolOutput,
        onValueChange = { path -> update { it.copy(poolOutput = path) } },
        mustExist = false,
        onBrowse = { outputPicker.launch() },
    )
    if (!showMapping) {
        EnumButtonGroup(
            entries = PoolModes,
            selected = state.poolKind,
            label = { it.label },
            onSelected = { mode -> update { it.copy(poolKind = mode) } },
        )
        TextSwitch(
            state.poolSimply,
            { simply -> update { it.copy(poolSimply = simply) } },
            "使用简单文本池模式",
        )
    }
}

@Composable
private fun GenerateMtlxFields(state: ToolboxState, update: Update) {
    val inputPicker = filePicker { path -> update { it.copy(poolInput = path) } }
    val outputPicker = poolOutputPicker(state.poolOutput, update)

    PathRow(
        label = "输入 JSON",
        value = state.poolInput,
        onValueChange = { path -> update { it.copy(poolInput = path) } },
        onBrowse = { inputPicker.launch() },
    )
    EnumButtonGroup(
        entries = MtlxSource.entries,
        selected = state.mtlxSource,
        label = { it.label },
        onSelected = { source -> update { it.copy(mtlxSource = source) } },
    )
    PathRow(
        label = "MTLX 输出文件",
        value = state.poolOutput,
        onValueChange = { path -> update { it.copy(poolOutput = path) } },
        mustExist = false,
        onBrowse = { outputPicker.launch() },
    )
}

@Composable
private fun TranslateMtlxFields(state: ToolboxState, update: Update) {
    val mtlxPicker = filePicker { path -> update { it.copy(mtlxInput = path) } }
    val poolPicker = filePicker { path -> update { it.copy(poolInput = path) } }
    val outputPicker = poolOutputPicker(state.poolOutput, update)

    PathRow(
        label = "MTLX 文件",
        value = state.mtlxInput,
        onValueChange = { path -> update { it.copy(mtlxInput = path) } },
        onBrowse = { mtlxPicker.launch() },
    )
    PathRow(
        label = "文本池 JSON",
        value = state.poolInput,
        onValueChange = { path -> update { it.copy(poolInput = path) } },
        onBrowse = { poolPicker.launch() },
    )
    PathRow(
        label = "映射输出 JSON",
        value = state.poolOutput,
        onValueChange = { path -> update { it.copy(poolOutput = path) } },
        mustExist = false,
        onBrowse = { outputPicker.launch() },
    )
}

@Composable
private fun ReplaceAllFields(state: ToolboxState, update: Update) {
    val inputPicker = filePicker { path -> update { it.copy(poolInput = path) } }
    val outputPicker = poolOutputPicker(state.poolOutput, update)

    PathRow(
        label = "提取结果 JSON",
        value = state.poolInput,
        onValueChange = { path -> update { it.copy(poolInput = path) } },
        onBrowse = { inputPicker.launch() },
    )
    PathRow(
        label = "替换输出 JSON",
        value = state.poolOutput,
        onValueChange = { path -> update { it.copy(poolOutput = path) } },
        mustExist = false,
        onBrowse = { outputPicker.launch() },
    )
    ConfigTextField(
        value = state.replacement,
        onValueChange = { text -> update { it.copy(replacement = text) } },
        label = { Text("固定替换内容") },
        singleLine = true,
    )
}

// ── Formats and maps ──────────────────────────────────────────

@Composable
private fun ConvertFields(state: ToolboxState, update: Update) {
    val convert = state.convert
    val inputPicker = filePicker { path -> update { it.copy(convert = it.convert.copy(input = path)) } }
    val workingDirPicker = directoryPicker { path -> update { it.copy(convert = it.convert.copy(currentDirectory = path)) } }
    val outputFilePicker = outputPicker(convert.output) { path ->
        update { it.copy(convert = it.convert.copy(output = path)) }
    }

    Text(
        "在 NBT、SNBT、JSON 之间互转；转换与压缩由 CLI 执行，日志会显示实际命令行。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TextSwitch(
        checked = convert.batch,
        onCheckedChange = { batch -> update { it.copy(convert = it.convert.copy(batch = batch)) } },
        text = "批量模式（输入按正则匹配多个文件）",
    )
    if (convert.batch) {
        ConfigTextField(
            value = convert.input,
            onValueChange = { text -> update { it.copy(convert = it.convert.copy(input = text)) } },
            label = { Text("文件路径正则") },
            placeholder = { Text("例如 .*map_.*\\.dat") },
            singleLine = true,
        )
        PathRow(
            label = "工作目录",
            placeholder = "从该目录递归匹配",
            value = convert.currentDirectory,
            onValueChange = { path -> update { it.copy(convert = it.convert.copy(currentDirectory = path)) } },
            onBrowse = { workingDirPicker.launch() },
        )
    } else {
        PathRow(
            label = "输入文件",
            placeholder = "要转换的文件",
            value = convert.input,
            onValueChange = { path -> update { it.copy(convert = it.convert.copy(input = path)) } },
            onBrowse = { inputPicker.launch() },
        )
    }
    Text(
        "输入格式",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    EnumButtonGroup(
        entries = ConvertFormat.entries,
        selected = convert.inputFormat,
        label = { it.label },
        onSelected = { format -> update { it.copy(convert = it.convert.copy(inputFormat = format)) } },
    )
    if (!convert.batch) {
        PathRow(
            label = "输出文件",
            value = convert.output,
            onValueChange = { path -> update { it.copy(convert = it.convert.copy(output = path)) } },
            mustExist = false,
            onBrowse = { outputFilePicker.launch() },
        )
    }
    Text(
        "输出格式",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    EnumButtonGroup(
        entries = ConvertFormat.entries,
        selected = convert.outputFormat,
        label = { it.label },
        onSelected = { format -> update { it.copy(convert = it.convert.copy(outputFormat = format)) } },
    )
    if (convert.batch && convert.outputFormat == ConvertFormat.Auto) {
        Text(
            "批量模式必须指定输出格式：每个匹配文件没有单独的扩展名可以推断。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    Text(
        "压缩",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    EnumButtonGroup(
        entries = ConvertCompression.entries,
        selected = convert.compression,
        label = { it.label },
        onSelected = { compression -> update { it.copy(convert = it.convert.copy(compression = compression)) } },
    )
    if (convert.compression != ConvertCompression.None) {
        ConfigTextField(
            value = convert.compressionLevel,
            onValueChange = { text -> update { it.copy(convert = it.convert.copy(compressionLevel = text)) } },
            label = { Text("压缩级别（1-9）") },
            placeholder = { Text("留空使用默认级别") },
            singleLine = true,
        )
    }
    TextSwitch(
        checked = convert.pretty,
        onCheckedChange = { pretty -> update { it.copy(convert = it.convert.copy(pretty = pretty)) } },
        text = "美化输出（缩进）",
    )
}

@Composable
private fun MapFileFields(
    state: ToolboxState,
    update: Update,
    isRunning: Boolean,
    mapController: MapToolController,
) {
    // Only the formats `mct kit map` decodes are offered: anything else fails in the decoder.
    val mapFilePicker = rememberFilePickerLauncher(
        type = FileKitType.File(listOf("dat")),
        mode = FileKitMode.Single,
    ) { file: PlatformFile? ->
        file?.let { picked -> update { it.copy(map = it.map.copy(input = platformPathOf(picked))) } }
    }
    val mapImagePicker = rememberFilePickerLauncher(
        type = FileKitType.Image,
        mode = FileKitMode.Single,
    ) { file: PlatformFile? ->
        file?.let { mapController.overwriteImage(platformPathOf(it)) }
    }

    Text(
        "预览由地图数据（colors）渲染：复写图像后显示的是量化后的颜色，" +
            "而不是所选图片本身。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    PathRow(
        label = "地图文件（data/map_*.dat）",
        placeholder = "选择要预览或复写的地图文件",
        value = state.map.input,
        onValueChange = { path -> update { it.copy(map = it.map.copy(input = path)) } },
        onBrowse = { mapFilePicker.launch() },
    )
    // The outcome of the last action, reported here because a snackbar can be covered by the page's
    // own scroll or missed while another tool is open.
    mapController.status?.let { status ->
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = if (status.error) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
        ) {
            Text(
                status.text,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = if (status.error) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                },
            )
        }
    }

    val map = mapController.mapFile
    if (map == null) {
        Text(
            "加载后这里显示预览，可导出为图片或由图片复写。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        MapPreviewAndActions(state, update, isRunning, mapController, map, mapImagePicker::launch)
    }
}

@Composable
private fun MapPreviewAndActions(
    state: ToolboxState,
    update: Update,
    isRunning: Boolean,
    mapController: MapToolController,
    map: MapFile,
    onPickImage: () -> Unit,
) {
    val imageOutputPicker = outputPicker(state.map.imageOutput) { path ->
        update { it.copy(map = it.map.copy(imageOutput = path)) }
    }
    // Keyed on the map instance: an overwrite replaces it, so the bitmap is rebuilt from the new
    // colors instead of showing the previous image.
    val preview = remember(map) { mapBitmap(map) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KiteImage(
                bitmap = preview,
                contentDescription = "地图预览",
                modifier = Modifier.size(224.dp),
                contentScale = ContentScale.Fit,
                // Nearest neighbour: a 128×128 map scaled up must stay crisp.
                filterQuality = FilterQuality.None,
            )
            Text(
                mapSummary(map),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Text(
        "导出格式",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    EnumButtonGroup(
        entries = MapImageFormat.entries,
        selected = state.map.imageFormat,
        label = { it.label },
        onSelected = { format -> update { it.copy(map = it.map.copy(imageFormat = format)) } },
    )
    PathRow(
        label = "图片输出文件",
        value = state.map.imageOutput,
        onValueChange = { path -> update { it.copy(map = it.map.copy(imageOutput = path)) } },
        mustExist = false,
        onBrowse = { imageOutputPicker.launch() },
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledTonalButton(
            onClick = { mapController.saveImage(state.map.imageOutput, state.map.imageFormat) },
            enabled = !isRunning && state.map.imageOutput.isNotBlank(),
            shapes = ButtonDefaults.shapes(),
            // Both actions share the row exactly, so they line up with the full-width controls above
            // instead of trailing off at the left.
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("保存图片")
        }
        OutlinedButton(
            onClick = onPickImage,
            enabled = !isRunning,
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("复写图像")
        }
    }
    Text(
        "复写图像会校验所选图片为 128×128，按地图颜色量化后写回地图文件。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ── Official language files ───────────────────────────────────

@Composable
private fun DownloadOfficialLanguageFields(state: ToolboxState, update: Update) {
    val outputPicker = directoryPicker { path -> update { it.copy(officialOutput = path) } }

    ConfigTextField(
        value = state.officialMinecraftVersion,
        onValueChange = { text -> update { it.copy(officialMinecraftVersion = text) } },
        label = { Text("Minecraft 版本") },
        placeholder = { Text("latest 或具体版本，如 1.21.5") },
        singleLine = true,
    )
    PathRow(
        label = "语言包输出目录",
        value = state.officialOutput,
        onValueChange = { path -> update { it.copy(officialOutput = path) } },
        mustExist = false,
        onBrowse = { outputPicker.launch() },
    )
    ConfigTextField(
        value = state.officialConcurrency,
        onValueChange = { text -> update { it.copy(officialConcurrency = text) } },
        label = { Text("下载并发数") },
        singleLine = true,
    )
}

@Composable
private fun CombineOfficialLanguageFields(state: ToolboxState, update: Update) {
    val sourcePicker = filePicker { path -> update { it.copy(officialSourceLanguage = path) } }
    val targetPicker = filePicker { path -> update { it.copy(officialTargetLanguage = path) } }
    val outputPicker = poolOutputPicker(state.poolOutput, update)

    PathRow(
        label = "源语言 JSON",
        value = state.officialSourceLanguage,
        onValueChange = { path -> update { it.copy(officialSourceLanguage = path) } },
        onBrowse = { sourcePicker.launch() },
    )
    PathRow(
        label = "目标语言 JSON",
        value = state.officialTargetLanguage,
        onValueChange = { path -> update { it.copy(officialTargetLanguage = path) } },
        onBrowse = { targetPicker.launch() },
    )
    PathRow(
        label = "术语表输出 JSON",
        value = state.poolOutput,
        onValueChange = { path -> update { it.copy(poolOutput = path) } },
        mustExist = false,
        onBrowse = { outputPicker.launch() },
    )
}

// ── Shared pieces ─────────────────────────────────────────────

/**
 * Fade a result card in and out.
 *
 * A size animation is deliberately absent: these cards are one or two lines, and animating their
 * height would re-measure everything below them for dozens of frames with no visual payoff.
 */
@Composable
private fun ResultCard(visible: Boolean, content: @Composable () -> Unit) {
    val motionScheme = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = motionScheme.defaultEffectsSpec()),
        exit = fadeOut(animationSpec = motionScheme.fastEffectsSpec()),
    ) { content() }
}

/**
 * A picker that reads a file the run consumes.
 *
 * A plain function rather than a shared launcher: a launcher's result belongs to the field that
 * opened it, so each field needs its own; these only remove the boilerplate.
 */
@Composable
private fun filePicker(onPicked: (String) -> Unit) =
    rememberFilePickerLauncher(type = FileKitType.File(), mode = FileKitMode.Single) { file: PlatformFile? ->
        file?.let { onPicked(platformPathOf(it)) }
    }

/** A picker for a directory the run reads from or writes into. */
@Composable
private fun directoryPicker(onPicked: (String) -> Unit) =
    rememberDirectoryPickerLauncher { file: PlatformFile? ->
        file?.let { onPicked(platformPathOf(it)) }
    }

/** The pool-output field's picker; every tool that writes a JSON names it `poolOutput`. */
@Composable
private fun poolOutputPicker(current: String, update: Update) =
    outputPicker(current) { path -> update { it.copy(poolOutput = path) } }

/**
 * A picker for a destination the run creates.
 *
 * It selects a *directory* and keeps [keepNameFrom]'s file name: the system picker creates files
 * only through its own UI, so the folder is what the user can choose, and the name already typed
 * into the field is what the run will write.
 */
@Composable
private fun outputPicker(keepNameFrom: String, onPicked: (String) -> Unit) =
    rememberDirectoryPickerLauncher { file: PlatformFile? ->
        file?.let { onPicked(joinPath(platformPathOf(it), outputFileName(keepNameFrom))) }
    }

/**
 * The file name to keep when the user picks an output directory.
 *
 * [value] is whatever is in the field, which is not always a path this app wrote: an older build
 * could leave a `content://` URI there, and taking its last segment would carry that whole URI into
 * the new destination as if it were a file name. Anything that is not a plain file name is therefore
 * discarded, and the caller's own default is used instead.
 */
private fun outputFileName(value: String): String {
    val name = fileNameOf(value)
    return if (name.isBlank() || name.contains(':') || name.contains('/')) "output.json" else name
}
