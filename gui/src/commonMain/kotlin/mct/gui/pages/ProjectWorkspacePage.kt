@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package mct.gui.pages

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mct.gui.components.CollapsibleActionButtonGroup
import mct.gui.components.HoverHint
import mct.gui.model.ProjectAction
import mct.gui.model.ProjectHistoryEntry
import mct.gui.model.ProjectSection
import mct.gui.model.ProjectTextFile
import mct.gui.services.PROJECT_FILE
import mct.gui.state.ProjectController

/**
 * An open project: its name top-left, the function area in the middle and the project actions
 * pinned underneath.
 *
 * The function area is the large card that morphs between the three function cards and the page
 * behind each of them; the action bar stays put, because `update` / `build` / `patch` act on the
 * whole project rather than on one page of it.
 */
@Composable
fun ProjectWorkspacePage(
    controller: ProjectController,
    project: ProjectHistoryEntry,
    isRunning: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        WorkspaceHeader(controller, project)
        FunctionArea(controller, modifier = Modifier.fillMaxWidth().weight(1f))
        ProjectActionBar(controller, isRunning)
    }
}

@Composable
private fun WorkspaceHeader(controller: ProjectController, project: ProjectHistoryEntry) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HoverHint("返回项目列表") {
            IconButton(onClick = controller::close) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回项目列表",
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                project.name,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                project.directory,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (controller.isDataLoading) {
            LoadingIndicator(modifier = Modifier.size(20.dp))
        }
        HoverHint("重新读取 mct.toml、映射与缺失列表") {
            IconButton(onClick = controller::refreshData, enabled = !controller.isDataLoading) {
                Icon(Icons.Outlined.Refresh, contentDescription = "刷新项目数据", modifier = Modifier.size(20.dp))
            }
        }
        HoverHint("在资源管理器中打开 $PROJECT_FILE") {
            IconButton(onClick = { controller.revealProjectFile(PROJECT_FILE) }) {
                Icon(Icons.Outlined.Description, contentDescription = "打开 $PROJECT_FILE", modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** The large content card: the three function cards, or the page one of them switches to. */
@Composable
private fun FunctionArea(controller: ProjectController, modifier: Modifier = Modifier) {
    val motionScheme = MaterialTheme.motionScheme
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        AnimatedContent(
            targetState = controller.section,
            transitionSpec = {
                // Material's "fade through": the two pages are not spatially related, so nothing
                // slides. A slide inside this card would spend most of the animation clipped by the
                // card's own rounded edge and read as a jump.
                val enter = fadeIn(animationSpec = motionScheme.defaultEffectsSpec()) +
                    scaleIn(animationSpec = motionScheme.defaultSpatialSpec(), initialScale = 0.94f)
                val exit = fadeOut(animationSpec = motionScheme.fastEffectsSpec()) +
                    scaleOut(animationSpec = motionScheme.fastSpatialSpec(), targetScale = 0.98f)
                enter togetherWith exit
            },
            modifier = Modifier.fillMaxSize(),
            label = "project-section",
        ) { section ->
            val textFile = section.textFile
            when {
                section == ProjectSection.Dashboard -> ProjectDashboardSection(controller)
                section == ProjectSection.Config -> ProjectConfigSection(controller)
                textFile != null -> ProjectTextListSection(controller, textFile)
                else -> Unit
            }
        }
    }
}

@Composable
private fun ProjectDashboardSection(controller: ProjectController) {
    val cards = buildList {
        add(
            ProjectFunctionCardData(
                title = "编辑项目配置",
                supporting = "查看并修改 mct.toml 的每一项，字段下方就是文件里的注释",
                file = PROJECT_FILE,
                trailing = controller.editor?.engineKind?.value?.label,
                icon = Icons.Outlined.Tune,
                container = MaterialTheme.colorScheme.primaryContainer,
                onContent = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = { controller.showSection(ProjectSection.Config) },
            )
        )
        ProjectTextFile.entries.forEach { textFile ->
            val empty = controller.entriesOf(textFile).isEmpty()
            add(
                ProjectFunctionCardData(
                    title = textFile.title,
                    supporting = textFile.description,
                    file = controller.pathOf(textFile),
                    trailing = "${controller.entriesOf(textFile).size} 条",
                    icon = textFile.icon,
                    container = textFile.containerColor(empty),
                    onContent = textFile.contentColor(empty),
                    onClick = { controller.showSection(textFile.section) },
                )
            )
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = if (maxWidth >= 720.dp) 2 else 1
        val spacing = 12.dp
        val rows = (cards.size + columns - 1) / columns
        // The rows share whatever height the function area has, but never drop below a comfortable
        // minimum: on a short area the column scrolls instead of clipping the rows.
        val perRow = ((maxHeight - FunctionAreaChrome - spacing * (rows - 1)) / rows)
            .coerceAtLeast(MinFunctionCardHeight)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("功能区", style = MaterialTheme.typography.titleLarge)
                Text(
                    "选择一个功能，这里会切换到对应的页面。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            cards.chunked(columns).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                ) {
                    row.forEach { card ->
                        ProjectFunctionCard(card, Modifier.weight(1f).height(perRow))
                    }
                    // Keep the cards in a short row at their column width instead of stretching them.
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** The section a text file's page lives in; kept here so the two enums stay independent. */
private val ProjectTextFile.section: ProjectSection
    get() = when (this) {
        ProjectTextFile.Mappings -> ProjectSection.Mappings
        ProjectTextFile.Missing -> ProjectSection.Missing
        ProjectTextFile.Terms -> ProjectSection.Terms
    }

/** Section header, card gaps and container padding that surround the function rows. */
private val FunctionAreaChrome = 128.dp

/** Smallest height a function row is allowed to shrink to before the area starts scrolling. */
private val MinFunctionCardHeight = 112.dp

/** Icon of every text file page, and the accent each function card uses. */
private val ProjectTextFile.icon: ImageVector
    get() = when (this) {
        ProjectTextFile.Mappings -> Icons.Outlined.Translate
        ProjectTextFile.Missing -> Icons.Outlined.ErrorOutline
        ProjectTextFile.Terms -> Icons.Outlined.Bookmark
    }

@Composable
private fun ProjectTextFile.containerColor(empty: Boolean): Color = when (this) {
    ProjectTextFile.Mappings -> MaterialTheme.colorScheme.secondaryContainer
    ProjectTextFile.Terms -> MaterialTheme.colorScheme.primaryContainer
    ProjectTextFile.Missing -> if (empty) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.tertiaryContainer
    }
}

@Composable
private fun ProjectTextFile.contentColor(empty: Boolean): Color = when (this) {
    ProjectTextFile.Mappings -> MaterialTheme.colorScheme.onSecondaryContainer
    ProjectTextFile.Terms -> MaterialTheme.colorScheme.onPrimaryContainer
    ProjectTextFile.Missing -> if (empty) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onTertiaryContainer
    }
}

/** Everything one function card shows; the layout decides how much room it gets. */
private data class ProjectFunctionCardData(
    val title: String,
    val supporting: String,
    val file: String,
    val trailing: String?,
    val icon: ImageVector,
    val container: Color,
    val onContent: Color,
    val onClick: () -> Unit,
)

@Composable
private fun ProjectFunctionCard(card: ProjectFunctionCardData, modifier: Modifier = Modifier) {
    Card(
        onClick = card.onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(modifier = Modifier.size(52.dp), shape = MaterialTheme.shapes.large, color = card.container) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(card.icon, contentDescription = null, tint = card.onContent)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    card.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    card.supporting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    card.file,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (card.trailing != null) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        card.trailing,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProjectActionBar(controller: ProjectController, isRunning: Boolean) {
    CollapsibleActionButtonGroup(
        entries = ProjectAction.entries,
        label = { it.label },
        icon = { it.icon },
        cancelLabel = { it.cancelLabel },
        enabled = !isRunning && controller.opened != null,
        running = controller.runningAction,
        onAction = controller::run,
        onCancel = controller::cancel,
        modifier = Modifier.fillMaxWidth(),
    )
}

private val ProjectAction.icon: ImageVector
    get() = when (this) {
        ProjectAction.Update -> Icons.Outlined.Update
        ProjectAction.Term -> Icons.Outlined.Bookmark
        ProjectAction.Translate -> Icons.Outlined.Translate
        ProjectAction.Build -> Icons.Outlined.Build
        ProjectAction.Patch -> Icons.Outlined.Difference
    }
