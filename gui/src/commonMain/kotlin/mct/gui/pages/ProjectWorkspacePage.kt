@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package mct.gui.pages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mct.gui.components.*
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
 * The function area is the shared one: the dashboard of function cards morphs into the page behind
 * whichever card was chosen. The action bar stays put, because `update` / `build` / `patch` act on
 * the whole project rather than on one page of it.
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
        FunctionArea(
            selected = controller.section.takeUnless { it == ProjectSection.Dashboard },
            onBack = { controller.showSection(ProjectSection.Dashboard) },
            modifier = Modifier.fillMaxWidth().weight(1f),
            catalogue = { ProjectDashboardSection(controller) },
            // The section pages carry a header of their own — search, counts, the save button — so
            // the area's back affordance is not the one they use.
            page = { section, _ -> ProjectSectionPage(controller, section) },
        )
        ProjectActionBar(controller, isRunning)
    }
}

@Composable
private fun WorkspaceHeader(controller: ProjectController, project: ProjectHistoryEntry) {
    // Two rows: the project's name and directory, then its actions. Sharing one row let the
    // weighted text column take the whole width, which at 500dp pushed the action icons out of the
    // header entirely.
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
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
}

/** The page behind one function card. */
@Composable
private fun ProjectSectionPage(controller: ProjectController, section: ProjectSection) {
    val textFile = section.textFile
    when {
        section == ProjectSection.Config -> ProjectConfigSection(controller)
        textFile != null -> ProjectTextListSection(controller, textFile)
        else -> Unit
    }
}

@Composable
private fun ProjectDashboardSection(controller: ProjectController) {
    val cards = buildList {
        add(
            FunctionCardSpec(
                key = ProjectSection.Config,
                title = "编辑项目配置",
                supporting = "查看并修改 mct.toml 的每一项，字段下方就是文件里的注释",
                icon = Icons.Outlined.Tune,
                detail = PROJECT_FILE,
                badge = controller.editor?.engineKind?.value?.label,
                accent = FunctionAccent.Primary,
            )
        )
        ProjectTextFile.entries.forEach { textFile ->
            val empty = controller.entriesOf(textFile).isEmpty()
            add(
                FunctionCardSpec(
                    key = textFile.section,
                    title = textFile.title,
                    supporting = textFile.description,
                    icon = textFile.icon,
                    detail = controller.pathOf(textFile),
                    badge = "${controller.entriesOf(textFile).size} 条",
                    accent = textFile.accent(empty),
                )
            )
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // One column until a card can hold its title, its path and its trailing count without
        // ellipsising the path, two once there is room — and never more: these cards carry a
        // description and a file path, and a third column makes every one of them cramped.
        val columns = if (maxWidth >= 560.dp) 2 else 1
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
            FunctionGrid(
                cards = cards,
                onSelect = controller::showSection,
                columns = columns,
                rowHeight = perRow,
            )
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

/** A file with nothing in it reads as neutral rather than as a problem to fix. */
private fun ProjectTextFile.accent(empty: Boolean): FunctionAccent = when (this) {
    ProjectTextFile.Mappings -> FunctionAccent.Secondary
    ProjectTextFile.Terms -> FunctionAccent.Primary
    ProjectTextFile.Missing -> if (empty) FunctionAccent.Neutral else FunctionAccent.Tertiary
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
        ProjectAction.Check -> Icons.AutoMirrored.Outlined.FactCheck
        ProjectAction.Term -> Icons.Outlined.Bookmark
        ProjectAction.Translate -> Icons.Outlined.Translate
        ProjectAction.Preprocessing -> Icons.Outlined.Handyman
        ProjectAction.Build -> Icons.Outlined.Build
        ProjectAction.Patch -> Icons.Outlined.Difference
    }
