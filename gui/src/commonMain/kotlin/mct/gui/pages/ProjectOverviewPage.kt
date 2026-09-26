@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package mct.gui.pages

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import mct.gui.components.SectionTitle
import mct.gui.components.rememberMissingPath
import mct.gui.model.ProjectHistoryEntry
import mct.gui.services.PROJECT_FILE
import mct.gui.state.ProjectController
import mct.gui.util.formatElapsed
import java.io.File

/**
 * Project overview: the projects opened before, most recent first, and the FAB menu that either
 * creates a new project (`mct project init`) or adopts one that already exists on disk.
 */
@Composable
fun ProjectOverviewPage(
    controller: ProjectController,
    isRunning: Boolean,
    modifier: Modifier = Modifier,
) {
    val history = controller.history
    var fabExpanded by remember { mutableStateOf(false) }
    val importPicker = rememberDirectoryPickerLauncher { file: PlatformFile? ->
        file?.let { picked -> controller.importProject(picked.absolutePath()) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProjectHero(projectCount = history.size)
            if (history.isEmpty()) {
                EmptyProjectsCard(
                    onCreate = controller::showInitDialog,
                    onImport = { importPicker.launch() },
                    enabled = !isRunning,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                SectionTitle("打开过的项目", Icons.Outlined.History)
                LazyColumn(
                    // Full-width rows rather than a grid: a project is identified by its directory,
                    // which is the widest thing on the card, so a fixed-width cell would either
                    // ellipsise it or leave the rest of the window empty.
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    // Room for the FAB menu, so it never covers the last card.
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    items(history, key = { it.directory }) { entry ->
                        ProjectHistoryCard(
                            entry = entry,
                            onOpen = { controller.open(entry) },
                            onReveal = { controller.revealPath(entry.directory) },
                            onForget = { controller.forget(entry) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }

        Box(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp)) {
            FloatingActionButtonMenu(
                expanded = fabExpanded,
                button = {
                    ToggleFloatingActionButton(
                        checked = fabExpanded,
                        onCheckedChange = { expanded -> fabExpanded = expanded },
                    ) {
                        AnimatedContent(
                            targetState = fabExpanded,
                            transitionSpec = {
                                (fadeIn() + scaleIn(initialScale = 0.7f)) togetherWith
                                    (fadeOut() + scaleOut(targetScale = 0.7f))
                            },
                            label = "fab-icon",
                        ) { expanded ->
                            Icon(
                                if (expanded) Icons.Outlined.Close else Icons.Outlined.Add,
                                contentDescription = if (expanded) "收起" else "新建或导入项目",
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                },
            ) {
                FloatingActionButtonMenuItem(
                    onClick = {
                        fabExpanded = false
                        controller.showInitDialog()
                    },
                    icon = { Icon(Icons.Outlined.CreateNewFolder, contentDescription = null) },
                    text = { Text("新建项目") },
                )
                FloatingActionButtonMenuItem(
                    onClick = {
                        fabExpanded = false
                        importPicker.launch()
                    },
                    icon = { Icon(Icons.Outlined.FileOpen, contentDescription = null) },
                    text = { Text("导入已有项目") },
                )
            }
        }
    }
}

@Composable
private fun ProjectHero(projectCount: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 2.dp,
    ) {
        // A narrow card cannot hold the icon, the copy and the count chip side by side: the text
        // wraps one character per line. The chip drops below the copy once the card is narrower
        // than a readable line.
        BoxWithConstraints(Modifier.padding(20.dp)) {
        val stacked = maxWidth < 420.dp
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                        Icons.Outlined.Workspaces,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            // The icon tile also drops below the copy on a narrow card: at 52dp plus the chip it
            // leaves the headline about two characters of width.
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "项目工作流",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "打开历史项目，或用右下角的 + 新建 / 导入项目。项目命令仍由 CLI 执行。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                if (stacked) {
                    Spacer(Modifier.height(4.dp))
                    ProjectCountChip(projectCount)
                }
            }
            if (!stacked) {
                ProjectCountChip(projectCount)
            }
        }
        }
    }
}

/** "N 个项目" badge of the overview hero. */
@Composable
private fun ProjectCountChip(projectCount: Int) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primary,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Outlined.History,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
            Text(
                "$projectCount 个项目",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EmptyProjectsCard(
    onCreate: () -> Unit,
    onImport: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Outlined.Workspaces,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text("还没有打开过项目", style = MaterialTheme.typography.titleLarge)
            Text(
                "新建：CLI 复制源存档并生成 mct.toml。导入：直接选择一个已经含有 mct.toml 的项目目录。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onCreate, enabled = enabled, shapes = ButtonDefaults.shapes()) {
                    Icon(Icons.Outlined.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("新建项目")
                }
                FilledTonalButton(onClick = onImport, enabled = enabled, shapes = ButtonDefaults.shapes()) {
                    Icon(Icons.Outlined.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("导入已有项目")
                }
            }
        }
    }
}

@Composable
private fun ProjectHistoryCard(
    entry: ProjectHistoryEntry,
    onOpen: () -> Unit,
    onReveal: () -> Unit,
    onForget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A project whose directory moved or lost its mct.toml can no longer be opened by the CLI, so
    // it is flagged here instead of failing on the first command.
    val unreachable = rememberMissingPath(File(entry.directory, PROJECT_FILE).path, mustExist = true)
    Card(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.Workspaces,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            // The elapsed time sits under the directory rather than beside the actions: as a
            // trailing item it took width the name and directory need, and on a 500dp window it
            // squeezed both to an ellipsis.
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    entry.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    entry.directory,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${formatElapsed(entry.lastOpenedAt)}" +
                        if (unreachable) " · 目录不存在或缺少 mct.toml" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (unreachable) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onReveal) {
                Icon(
                    Icons.Outlined.FolderOpen,
                    contentDescription = "在资源管理器中打开",
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = onForget) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = "从历史中移除",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
