package mct.gui.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
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
import androidx.compose.ui.text.style.TextOverflow
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

private data class ToolboxAction(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val operation: ToolboxOperation,
)

private data class ToolboxSectionSpec(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val tools: List<ToolboxAction>,
)

/**
 * The toolbox's three sections. Hoisted to file level: building these lists in composition
 * would hand [ToolboxSection] a new `List` instance on every recomposition, so it and the
 * card subtree could never skip.
 */
private val ToolboxSections = listOf(
    ToolboxSectionSpec(
        title = "文本与翻译",
        description = "整理文本池、映射和 MTLX，让翻译前后的数据转换保持可重复。",
        icon = Icons.Outlined.Translate,
        tools = listOf(
            ToolboxAction("生成文本池", "把提取结果整理为唯一文本池。", Icons.Outlined.AccountTree, ToolboxOperation.FlattenPool),
            ToolboxAction("应用文本映射", "把 mapping 还原为回填替换组。", Icons.AutoMirrored.Outlined.MergeType, ToolboxOperation.UnflattenPool),
            ToolboxAction("生成 MTLX", "从文本池生成结构化翻译模板。", Icons.Outlined.Description, ToolboxOperation.GenerateMtlx),
            ToolboxAction("翻译 MTLX", "执行 MTLX 映射并保留原有结构。", Icons.Outlined.Translate, ToolboxOperation.TranslateMtlx),
            ToolboxAction("批量替换", "为所有提取文本生成固定替换。", Icons.Outlined.FindReplace, ToolboxOperation.ReplaceAll),
        ),
    ),
    ToolboxSectionSpec(
        title = "规则与数据检查",
        description = "集中测试匹配规则、导出 schema，并检查存档中的原始 NBT 数据。",
        icon = Icons.AutoMirrored.Outlined.Rule,
        tools = listOf(
            ToolboxAction("DataPointer 测试", "验证内置或自定义指针过滤规则。", Icons.Outlined.GpsFixed, ToolboxOperation.PointerTest),
            ToolboxAction("Command Pattern 测试", "用样例输入验证命令提取模式。", Icons.Outlined.Terminal, ToolboxOperation.CommandTest),
            ToolboxAction("导出 Schema", "导出规则配置使用的 JSON Schema。", Icons.Outlined.Schema, ToolboxOperation.ExportSchema),
            ToolboxAction("导出 Region SNBT", "把 Region NBT 导出为可读 SNBT。", Icons.Outlined.DataObject, ToolboxOperation.ExportSnbt),
        ),
    ),
    ToolboxSectionSpec(
        title = "格式与地图",
        description = "在 NBT、SNBT、JSON 之间互转数据，并预览、导出或复写地图文件。",
        icon = Icons.Outlined.Map,
        tools = listOf(
            ToolboxAction("格式转换", "在 NBT、SNBT、JSON 之间互转，可批量处理。", Icons.Outlined.SwapHoriz, ToolboxOperation.Convert),
            ToolboxAction("地图查看与编辑", "由地图数据渲染预览，导出为图片或复写为地图。", Icons.Outlined.Map, ToolboxOperation.MapFile),
        ),
    ),
    ToolboxSectionSpec(
        title = "官方语言资源",
        description = "下载 Minecraft 官方语言文件，或把两种语言合并为术语表。",
        icon = Icons.Outlined.Language,
        tools = listOf(
            ToolboxAction("下载官方语言", "获取指定版本的官方语言资源。", Icons.Outlined.Download, ToolboxOperation.DownloadOfficialLanguage),
            ToolboxAction("合并官方语言", "由源语言和目标语言生成术语表。", Icons.AutoMirrored.Outlined.CompareArrows, ToolboxOperation.CombineOfficialLanguage),
        ),
    ),
)

/** Pool kinds: every extraction mode emits an `ExtractionGroup` list the pool engine accepts. */
private val PoolModes = RunMode.entries

@Composable
private fun ToolboxHero(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primary,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.Handyman,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "MCT 工具箱",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ToolboxSection(
    title: String,
    description: String,
    icon: ImageVector,
    tools: List<ToolboxAction>,
    onClick: (ToolboxOperation) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(title, icon)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Two at most: these cards carry a title, a description and a trailing arrow, and a
            // third column wraps every one of them.
            val columns = if (maxWidth >= 420.dp) 2 else 1
            val spacing = 12.dp
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                maxItemsInEachRow = columns,
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalArrangement = Arrangement.spacedBy(spacing),
            ) {
                tools.forEach { action ->
                    ToolboxActionCard(
                        action = action,
                        onClick = { onClick(action.operation) },
                        // FlowRow divides each row by weight; never compute card widths from maxWidth.
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolboxActionCard(
    action: ToolboxAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.heightIn(min = 132.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            action.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
                Text(action.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
fun ToolboxPanel(
    state: ToolboxState,
    onStateChange: (ToolboxState) -> Unit,
    isRunning: Boolean,
    onRunOperation: (ToolboxOperation) -> Unit,
    /**
     * The map tool's decoded map. Held by the view model rather than this panel: the preview has to
     * survive the dialog being closed, and it is drawn from that map's colors.
     */
    mapController: MapToolController,
    modifier: Modifier = Modifier,
) {
    // Every keystroke in the dialog changes `state`, so the callback reads the latest value
    // instead of capturing it; a captured state would change the callback's identity and
    // recompose the whole card grid behind the dialog.
    val currentState by rememberUpdatedState(state)
    val openOperation: (ToolboxOperation) -> Unit = remember(onStateChange) {
        { operation -> onStateChange(currentState.copy(activeOperation = operation)) }
    }

    Column(modifier = modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        // The catalogue only needs [openOperation]; keeping it a separate composable lets it
        // skip entirely while the dialog's state changes on every keystroke.
        ToolboxCatalogue(onClick = openOperation)
    }

    state.activeOperation?.let { operation ->
        ToolboxOperationDialog(
            operation = operation,
            state = state,
            isRunning = isRunning,
            onStateChange = onStateChange,
            mapController = mapController,
            onDismiss = { onStateChange(state.copy(activeOperation = null)) },
            onConfirm = { onRunOperation(operation) },
        )
    }
}

/**
 * Static catalogue of every tool. Takes nothing but the open callback, so it is skippable.
 */
@Composable
private fun ToolboxCatalogue(onClick: (ToolboxOperation) -> Unit) {
    ToolboxHero()
    ToolboxSections.forEach { section ->
        ToolboxSection(
            title = section.title,
            description = section.description,
            icon = section.icon,
            tools = section.tools,
            onClick = onClick,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ToolboxOperationDialog(
    operation: ToolboxOperation,
    state: ToolboxState,
    isRunning: Boolean,
    onStateChange: (ToolboxState) -> Unit,
    mapController: MapToolController,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme
    // Stable identity for the rule editor's remembered callbacks.
    val currentState by rememberUpdatedState(state)
    val onPatternsChange: (MCTPatternState) -> Unit = remember(onStateChange) {
        // The command test's result card belongs to the rules it ran with; keeping it across a rule
        // change would show the previous run's matches as if they were the current ones.
        { updated -> onStateChange(currentState.copy(commandPatterns = updated, commandResult = "")) }
    }
    // Two factories cover every field: [filePicker] reads a file the run consumes, [directoryPicker]
    // chooses a directory. A destination takes the directory picker because the system picker cannot
    // create a file the way the run will, so the folder is chosen and the file name is typed;
    // [outputPicker] is the same thing for a field that already holds a file name to keep.
    // Each field gets its own launcher: a launcher's result belongs to the field that opened it.
    val pointerPatternPicker = filePicker { onStateChange(state.copy(pointerPatternPath = it)) }
    val exportInputPicker = directoryPicker { onStateChange(state.copy(exportInput = it)) }
    val exportOutputPicker = directoryPicker { onStateChange(state.copy(exportOutput = it)) }
    val convertInputPicker = filePicker { onStateChange(state.copy(convert = state.convert.copy(input = it))) }
    val convertDirectoryPicker = directoryPicker { onStateChange(state.copy(convert = state.convert.copy(currentDirectory = it))) }
    val convertOutputPicker = outputPicker(state.convert.output) {
        onStateChange(state.copy(convert = state.convert.copy(output = it)))
    }
    val poolInputPicker = filePicker { onStateChange(state.copy(poolInput = it)) }
    val poolOutputPicker = outputPicker(state.poolOutput) { onStateChange(state.copy(poolOutput = it)) }
    val mtlxInputPicker = filePicker { onStateChange(state.copy(mtlxInput = it)) }
    val commandInputPicker = filePicker { onStateChange(state.copy(commandInput = it)) }
    val mapImageOutputPicker = outputPicker(state.map.imageOutput) {
        onStateChange(state.copy(map = state.map.copy(imageOutput = it)))
    }
    val officialOutputPicker = directoryPicker { onStateChange(state.copy(officialOutput = it)) }
    val officialSourcePicker = filePicker { onStateChange(state.copy(officialSourceLanguage = it)) }
    val officialTargetPicker = filePicker { onStateChange(state.copy(officialTargetLanguage = it)) }
    // Only the formats `mct kit map` decodes are offered: anything else fails in the decoder.
    val mapFilePicker = rememberFilePickerLauncher(
        type = FileKitType.File(listOf("dat")),
        mode = FileKitMode.Single,
    ) { file: PlatformFile? ->
        file?.let { onStateChange(state.copy(map = state.map.copy(input = platformPathOf(it)))) }
    }
    val mapImagePicker = rememberFilePickerLauncher(
        type = FileKitType.Image,
        mode = FileKitMode.Single,
    ) { file: PlatformFile? ->
        file?.let { mapController.overwriteImage(platformPathOf(it)) }
    }

    AlertDialog(
        // The "关闭" button is disabled while the operation runs; the scrim and Escape must not be a
        // way around that, or the result would land in a dialog the user cannot see.
        onDismissRequest = { if (!isRunning) onDismiss() },
        icon = { Icon(operation.icon(), contentDescription = null) },
        title = { Text(operation.title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (operation) {
                    ToolboxOperation.PointerTest -> {
                        EnumButtonGroup(
                            entries = PointerKind.entries,
                            selected = state.pointerKind,
                            label = { it.label },
                            onSelected = { onStateChange(state.copy(pointerKind = it, pointerResult = null)) },
                        )
                        PathRow(
                            label = "自定义规则 JSON（可选）",
                            placeholder = "留空则仅使用内置规则",
                            value = state.pointerPatternPath,
                            onValueChange = {
                                onStateChange(
                                    state.copy(
                                        pointerPatternPath = it,
                                        pointerResult = null
                                    )
                                )
                            },
                            onBrowse = { pointerPatternPicker.launch() },
                        )
                        TextSwitch(
                            checked = state.noBuiltin,
                            onCheckedChange = { onStateChange(state.copy(noBuiltin = it, pointerResult = null)) },
                            text = "禁用内置规则",
                        )
                        ConfigTextField(
                            value = state.pointerInput,
                            onValueChange = { onStateChange(state.copy(pointerInput = it, pointerResult = null)) },
                            label = { Text("DataPointer 字符串") },
                            placeholder = { Text("例如 >#display>#Name") },
                            singleLine = true,
                        )
                        // Short one-line cards: a size animation would re-measure the dialog's
                        // remaining content for dozens of frames with no visual payoff.
                        AnimatedVisibility(
                            visible = state.pointerResult != null,
                            enter = fadeIn(animationSpec = motionScheme.defaultEffectsSpec()),
                            exit = fadeOut(animationSpec = motionScheme.fastEffectsSpec()),
                        ) {
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

                    ToolboxOperation.ExportSnbt -> {
                        Text(
                            "将存档内的 Region NBT 导出为可读 SNBT，便于调试和审查。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        PathRow(
                            label = "Minecraft 存档目录",
                            placeholder = "选择包含 level.dat 的目录",
                            value = state.exportInput,
                            onValueChange = { onStateChange(state.copy(exportInput = it)) },
                            onBrowse = { exportInputPicker.launch() },
                        )
                        PathRow(
                            label = "SNBT 导出目录",
                            placeholder = "选择保存位置",
                            value = state.exportOutput,
                            onValueChange = { onStateChange(state.copy(exportOutput = it)) },
                            mustExist = false,
                            onBrowse = { exportOutputPicker.launch() },
                        )
                    }

                    ToolboxOperation.FlattenPool -> PoolFields(state, onStateChange, showMapping = false)
                    ToolboxOperation.UnflattenPool -> PoolFields(state, onStateChange, showMapping = true)
                    ToolboxOperation.GenerateMtlx -> {
                        PathRow(
                                label = "输入 JSON",
                                value = state.poolInput,
                                onValueChange = { onStateChange(state.copy(poolInput = it)) },
                                onBrowse = { poolInputPicker.launch() },
                            )
                        EnumButtonGroup(
                            entries = MtlxSource.entries,
                            selected = state.mtlxSource,
                            label = { it.label },
                            onSelected = { onStateChange(state.copy(mtlxSource = it)) },
                        )
                        PathRow(
                                label = "MTLX 输出文件",
                                value = state.poolOutput,
                                onValueChange = { onStateChange(state.copy(poolOutput = it)) },
                                mustExist = false,
                                onBrowse = { poolOutputPicker.launch() },
                            )
                    }
                    ToolboxOperation.TranslateMtlx -> {
                        PathRow(
                                label = "MTLX 文件",
                                value = state.mtlxInput,
                                onValueChange = { onStateChange(state.copy(mtlxInput = it)) },
                                onBrowse = { mtlxInputPicker.launch() },
                            )
                        PathRow(
                                label = "文本池 JSON",
                                value = state.poolInput,
                                onValueChange = { onStateChange(state.copy(poolInput = it)) },
                                onBrowse = { poolInputPicker.launch() },
                            )
                        PathRow(
                                label = "映射输出 JSON",
                                value = state.poolOutput,
                                onValueChange = { onStateChange(state.copy(poolOutput = it)) },
                                mustExist = false,
                                onBrowse = { poolOutputPicker.launch() },
                            )
                    }
                    ToolboxOperation.ReplaceAll -> {
                        PathRow(
                                label = "提取结果 JSON",
                                value = state.poolInput,
                                onValueChange = { onStateChange(state.copy(poolInput = it)) },
                                onBrowse = { poolInputPicker.launch() },
                            )
                        PathRow(
                                label = "替换输出 JSON",
                                value = state.poolOutput,
                                onValueChange = { onStateChange(state.copy(poolOutput = it)) },
                                mustExist = false,
                                onBrowse = { poolOutputPicker.launch() },
                            )
                        ConfigTextField(
                            value = state.replacement,
                            onValueChange = { onStateChange(state.copy(replacement = it)) },
                            label = { Text("固定替换内容") },
                            singleLine = true,
                        )
                    }
                    ToolboxOperation.ExportSchema -> {
                        Text(
                            "选择需要导出的规则结构。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        EnumButtonGroup(
                            entries = SchemaKind.entries,
                            selected = state.schemaKind,
                            label = { it.label },
                            onSelected = { onStateChange(state.copy(schemaKind = it)) },
                        )
                        PathRow(
                                label = "Schema 输出 JSON",
                                value = state.poolOutput,
                                onValueChange = { onStateChange(state.copy(poolOutput = it)) },
                                mustExist = false,
                                onBrowse = { poolOutputPicker.launch() },
                            )
                    }
                    ToolboxOperation.CommandTest -> {
                        PathRow(
                                label = "命令样例文件",
                                value = state.commandInput,
                                onValueChange = { onStateChange(state.copy(commandInput = it, commandResult = "")) },
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
                        AnimatedVisibility(
                            visible = state.commandResult.isNotBlank(),
                            enter = fadeIn(animationSpec = motionScheme.defaultEffectsSpec()),
                            exit = fadeOut(animationSpec = motionScheme.fastEffectsSpec()),
                        ) {
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
                    ToolboxOperation.Convert -> {
                        Text(
                            "在 NBT、SNBT、JSON 之间互转；转换与压缩由 CLI 执行，日志会显示实际命令行。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextSwitch(
                            checked = state.convert.batch,
                            onCheckedChange = { onStateChange(state.copy(convert = state.convert.copy(batch = it))) },
                            text = "批量模式（输入按正则匹配多个文件）",
                        )
                        if (state.convert.batch) {
                            ConfigTextField(
                                value = state.convert.input,
                                onValueChange = { onStateChange(state.copy(convert = state.convert.copy(input = it))) },
                                label = { Text("文件路径正则") },
                                placeholder = { Text("例如 .*map_.*\\.dat") },
                                singleLine = true,
                            )
                            PathRow(
                                label = "工作目录",
                                placeholder = "从该目录递归匹配",
                                value = state.convert.currentDirectory,
                                onValueChange = {
                                    onStateChange(state.copy(convert = state.convert.copy(currentDirectory = it)))
                                },
                                onBrowse = { convertDirectoryPicker.launch() },
                            )
                        } else {
                            PathRow(
                                label = "输入文件",
                                placeholder = "要转换的文件",
                                value = state.convert.input,
                                onValueChange = { onStateChange(state.copy(convert = state.convert.copy(input = it))) },
                                onBrowse = { convertInputPicker.launch() },
                            )
                        }
                        Text(
                            "输入格式",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        EnumButtonGroup(
                            entries = ConvertFormat.entries,
                            selected = state.convert.inputFormat,
                            label = { it.label },
                            onSelected = { onStateChange(state.copy(convert = state.convert.copy(inputFormat = it))) },
                        )
                        if (!state.convert.batch) {
                            PathRow(
                                label = "输出文件",
                                value = state.convert.output,
                                onValueChange = { onStateChange(state.copy(convert = state.convert.copy(output = it))) },
                                mustExist = false,
                                onBrowse = { convertOutputPicker.launch() },
                            )
                        }
                        Text(
                            "输出格式",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        EnumButtonGroup(
                            entries = ConvertFormat.entries,
                            selected = state.convert.outputFormat,
                            label = { it.label },
                            onSelected = { onStateChange(state.copy(convert = state.convert.copy(outputFormat = it))) },
                        )
                        if (state.convert.batch && state.convert.outputFormat == ConvertFormat.Auto) {
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
                            selected = state.convert.compression,
                            label = { it.label },
                            onSelected = { onStateChange(state.copy(convert = state.convert.copy(compression = it))) },
                        )
                        if (state.convert.compression != ConvertCompression.None) {
                            ConfigTextField(
                                value = state.convert.compressionLevel,
                                onValueChange = {
                                    onStateChange(state.copy(convert = state.convert.copy(compressionLevel = it)))
                                },
                                label = { Text("压缩级别（1-9）") },
                                placeholder = { Text("留空使用默认级别") },
                                singleLine = true,
                            )
                        }
                        TextSwitch(
                            checked = state.convert.pretty,
                            onCheckedChange = { onStateChange(state.copy(convert = state.convert.copy(pretty = it))) },
                            text = "美化输出（缩进）",
                        )
                    }

                    ToolboxOperation.MapFile -> {
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
                            onValueChange = { onStateChange(state.copy(map = state.map.copy(input = it))) },
                            onBrowse = { mapFilePicker.launch() },
                        )
                        // The outcome of the last action, reported here because a snackbar would be
                        // covered by this dialog.
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
                            // Keyed on the map instance: an overwrite replaces it, so the bitmap is
                            // rebuilt from the new colors instead of showing the previous image.
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
                                onSelected = { onStateChange(state.copy(map = state.map.copy(imageFormat = it))) },
                            )
                            PathRow(
                                label = "图片输出文件",
                                value = state.map.imageOutput,
                                onValueChange = { onStateChange(state.copy(map = state.map.copy(imageOutput = it))) },
                                mustExist = false,
                                onBrowse = { mapImageOutputPicker.launch() },
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilledTonalButton(
                                    onClick = { mapController.saveImage(state.map.imageOutput, state.map.imageFormat) },
                                    enabled = !isRunning && state.map.imageOutput.isNotBlank(),
                                    shapes = ButtonDefaults.shapes(),
                                    // Both actions share the row exactly, so they line up with the
                                    // full-width controls above instead of trailing off at the left.
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("保存图片")
                                }
                                OutlinedButton(
                                    onClick = { mapImagePicker.launch() },
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
                    }

                    ToolboxOperation.DownloadOfficialLanguage -> {
                        ConfigTextField(
                            value = state.officialMinecraftVersion,
                            onValueChange = { onStateChange(state.copy(officialMinecraftVersion = it)) },
                            label = { Text("Minecraft 版本") },
                            placeholder = { Text("latest 或具体版本，如 1.21.5") },
                            singleLine = true,
                        )
                        PathRow(
                                label = "语言包输出目录",
                                value = state.officialOutput,
                                onValueChange = { onStateChange(state.copy(officialOutput = it)) },
                                mustExist = false,
                                onBrowse = { officialOutputPicker.launch() },
                            )
                        ConfigTextField(
                            value = state.officialConcurrency,
                            onValueChange = { onStateChange(state.copy(officialConcurrency = it)) },
                            label = { Text("下载并发数") },
                            singleLine = true,
                        )
                    }
                    ToolboxOperation.CombineOfficialLanguage -> {
                        PathRow(
                                label = "源语言 JSON",
                                value = state.officialSourceLanguage,
                                onValueChange = { onStateChange(state.copy(officialSourceLanguage = it)) },
                                onBrowse = { officialSourcePicker.launch() },
                            )
                        PathRow(
                                label = "目标语言 JSON",
                                value = state.officialTargetLanguage,
                                onValueChange = { onStateChange(state.copy(officialTargetLanguage = it)) },
                                onBrowse = { officialTargetPicker.launch() },
                            )
                        PathRow(
                                label = "术语表输出 JSON",
                                value = state.poolOutput,
                                onValueChange = { onStateChange(state.copy(poolOutput = it)) },
                                mustExist = false,
                                onBrowse = { poolOutputPicker.launch() },
                            )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isRunning && operation.isReady(state),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                if (isRunning) {
                    LoadingIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (isRunning) "执行中" else operation.actionLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isRunning, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("关闭")
            }
        },
    )
}

@Composable
private fun PoolFields(
    state: ToolboxState,
    onStateChange: (ToolboxState) -> Unit,
    showMapping: Boolean,
) {
    val inputPicker = filePicker { onStateChange(state.copy(poolInput = it)) }
    val mappingPicker = filePicker { onStateChange(state.copy(mappingInput = it)) }
    PathRow(
                                label = "提取结果 JSON",
                                value = state.poolInput,
                                onValueChange = { onStateChange(state.copy(poolInput = it)) },
                                onBrowse = { inputPicker.launch() },
                            )
    if (showMapping) PathRow(
                                label = "映射 JSON",
                                value = state.mappingInput,
                                onValueChange = { onStateChange(state.copy(mappingInput = it)) },
                                onBrowse = { mappingPicker.launch() },
                            )
    PathRow(
        label = if (showMapping) "替换输出 JSON" else "文本池输出 JSON",
        value = state.poolOutput,
        onValueChange = { onStateChange(state.copy(poolOutput = it)) },
        mustExist = false,
    )
    if (!showMapping) {
        EnumButtonGroup(
            entries = PoolModes,
            selected = state.poolKind,
            label = { it.label },
            onSelected = { onStateChange(state.copy(poolKind = it)) },
        )
        TextSwitch(state.poolSimply, { onStateChange(state.copy(poolSimply = it)) }, "使用简单文本池模式")
    }
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


private fun ToolboxOperation.icon(): ImageVector = when (this) {
    ToolboxOperation.PointerTest -> Icons.Outlined.GpsFixed
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

private fun ToolboxOperation.isReady(state: ToolboxState): Boolean = when (this) {
    ToolboxOperation.PointerTest -> state.pointerInput.isNotBlank()
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
