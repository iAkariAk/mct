package mct.gui.pages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.automirrored.outlined.MergeType
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mct.gui.components.*
import mct.gui.model.RunMode
import mct.gui.model.ToolboxOperation
import mct.gui.model.ToolboxState
import mct.gui.state.MapToolController

/**
 * The toolbox: a catalogue of every file-to-file utility, and the page of the one that is open.
 *
 * The catalogue and the tool pages are the shared function area, so opening a tool switches the
 * area instead of covering it — the same interaction the project workspace uses for its sections.
 * The open tool is part of [ToolboxState], so switching tabs and coming back returns to it.
 */
@Composable
fun ToolboxPanel(
    state: ToolboxState,
    onStateChange: (ToolboxState) -> Unit,
    isRunning: Boolean,
    onRunOperation: (ToolboxOperation) -> Unit,
    onCancelOperation: () -> Unit,
    /**
     * The map tool's decoded map. Held by the view model rather than this panel: the preview has to
     * survive the page being left, and it is drawn from that map's colors.
     */
    mapController: MapToolController,
    modifier: Modifier = Modifier,
) {
    FunctionArea(
        selected = state.activeOperation,
        onBack = { onStateChange(state.copy(activeOperation = null)) },
        modifier = modifier.padding(16.dp),
        catalogue = { ToolboxCatalogue(onSelect = { onStateChange(state.copy(activeOperation = it)) }) },
        page = { operation, back ->
            ToolboxOperationPage(
                operation = operation,
                state = state,
                isRunning = isRunning,
                onStateChange = onStateChange,
                onBack = back,
                onRun = { onRunOperation(operation) },
                onCancel = onCancelOperation,
                mapController = mapController,
            )
        },
    )
}

/** One tool as the catalogue describes it: its page title comes from the operation itself. */
private data class ToolSpec(
    val operation: ToolboxOperation,
    val supporting: String,
    val icon: ImageVector,
)

private fun tool(operation: ToolboxOperation, supporting: String, icon: ImageVector) =
    ToolSpec(operation, supporting, icon)

private fun toolboxGroup(
    title: String,
    description: String,
    icon: ImageVector,
    accent: FunctionAccent,
    tools: List<ToolSpec>,
) = FunctionGroupSpec(
    title = title,
    description = description,
    icon = icon,
    cards = tools.map { it.card(accent) },
)

private fun ToolSpec.card(accent: FunctionAccent) = FunctionCardSpec(
    key = operation,
    title = operation.title,
    supporting = supporting,
    icon = icon,
    accent = accent,
)

/**
 * The toolbox's sections. Hoisted to file level: building these lists in composition would hand the
 * grid a new `List` instance on every recomposition, so the card subtree could never skip.
 */
internal val ToolboxGroups: List<FunctionGroupSpec<ToolboxOperation>> = listOf(
    toolboxGroup(
        title = "文本与翻译",
        description = "整理文本池、映射和 MTLX，让翻译前后的数据转换保持可重复。",
        icon = Icons.Outlined.Translate,
        accent = FunctionAccent.Primary,
        tools = listOf(
            tool(ToolboxOperation.FlattenPool, "把提取结果整理为唯一文本池。", Icons.Outlined.AccountTree),
            tool(ToolboxOperation.UnflattenPool, "把 mapping 还原为回填替换组。", Icons.AutoMirrored.Outlined.MergeType),
            tool(ToolboxOperation.GenerateMtlx, "从文本池生成结构化翻译模板。", Icons.Outlined.Description),
            tool(ToolboxOperation.TranslateMtlx, "执行 MTLX 映射并保留原有结构。", Icons.Outlined.Translate),
            tool(ToolboxOperation.ReplaceAll, "为所有提取文本生成固定替换。", Icons.Outlined.FindReplace),
            tool(ToolboxOperation.ComponentPreview, "按游戏的方式预览一段文本组件。", Icons.Outlined.FormatColorText),
        ),
    ),
    toolboxGroup(
        title = "规则与数据检查",
        description = "集中测试匹配规则、查看合并后的规则，并检查存档中的原始 NBT 数据。",
        icon = Icons.AutoMirrored.Outlined.Rule,
        accent = FunctionAccent.Secondary,
        tools = listOf(
            tool(ToolboxOperation.PointerTest, "验证内置或自定义指针过滤规则。", Icons.Outlined.GpsFixed),
            tool(ToolboxOperation.CommandTest, "用样例输入验证命令提取模式。", Icons.Outlined.Terminal),
            tool(ToolboxOperation.PatternInspect, "查看内置规则与自定义文件合并后的结果。", Icons.AutoMirrored.Outlined.Rule),
            tool(ToolboxOperation.ExportSchema, "导出规则配置使用的 JSON Schema。", Icons.Outlined.Schema),
            tool(ToolboxOperation.ExportSnbt, "把 Region NBT 导出为可读 SNBT。", Icons.Outlined.DataObject),
        ),
    ),
    toolboxGroup(
        title = "格式与地图",
        description = "在 NBT、SNBT、JSON 之间互转数据，并预览、导出或复写地图文件。",
        icon = Icons.Outlined.Map,
        accent = FunctionAccent.Tertiary,
        tools = listOf(
            tool(ToolboxOperation.Convert, "在 NBT、SNBT、JSON 之间互转，可批量处理。", Icons.Outlined.SwapHoriz),
            tool(ToolboxOperation.MapFile, "由地图数据渲染预览，导出为图片或复写为地图。", Icons.Outlined.Map),
        ),
    ),
    toolboxGroup(
        title = "官方语言资源",
        description = "下载 Minecraft 官方语言文件，或把两种语言合并为术语表。",
        icon = Icons.Outlined.Language,
        accent = FunctionAccent.Secondary,
        tools = listOf(
            tool(ToolboxOperation.DownloadOfficialLanguage, "获取指定版本的官方语言资源。", Icons.Outlined.Download),
            tool(ToolboxOperation.CombineOfficialLanguage, "由源语言和目标语言生成术语表。", Icons.AutoMirrored.Outlined.CompareArrows),
        ),
    ),
)

/**
 * Every card by its operation, so a page can describe itself with the same sentence as the
 * catalogue. A tool missing from [ToolboxGroups] is a bug, and fails here rather than silently
 * becoming unreachable.
 */
internal val ToolboxCards: Map<ToolboxOperation, FunctionCardSpec<ToolboxOperation>> =
    ToolboxGroups.flatMap { it.cards }.associateBy { it.key }

/** Pool kinds: every extraction mode emits an `ExtractionGroup` list the pool engine accepts. */
internal val PoolModes = RunMode.entries

@Composable
private fun ToolboxCatalogue(onSelect: (ToolboxOperation) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        ToolboxHero()
        ToolboxGroups.forEach { group ->
            FunctionGroupBlock(
                spec = group,
                onSelect = onSelect,
                // Two columns once a card can hold its title and description together; a third
                // wraps every one of them.
                twoColumnWidth = 420.dp,
                minCardHeight = 132.dp,
            )
        }
    }
}

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
                Text(
                    "选择一个工具，这里会切换到它的页面。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}
