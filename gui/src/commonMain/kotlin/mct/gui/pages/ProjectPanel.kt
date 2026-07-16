package mct.gui.pages

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import mct.gui.components.PathRow
import mct.gui.components.SectionTitle
import mct.gui.model.ProjectWorkflowState

private data class ProjectStep(
    val number: Int,
    val title: String,
    val icon: ImageVector,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

@Composable
fun ProjectPanel(
    state: ProjectWorkflowState,
    onStateChange: (ProjectWorkflowState) -> Unit,
    isRunning: Boolean,
    onInit: () -> Unit,
    onUpdate: () -> Unit,
    onTerms: () -> Unit,
    onTranslate: () -> Unit,
    onBuild: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val projectDirectoryPicker = rememberDirectoryPickerLauncher { file: PlatformFile? ->
        file?.let { onStateChange(state.copy(directory = it.absolutePath())) }
    }
    val sourceDirectoryPicker = rememberDirectoryPickerLauncher { file: PlatformFile? ->
        file?.let { onStateChange(state.copy(source = it.absolutePath())) }
    }
    val hasProjectDirectory = state.directory.isNotBlank()

    Column(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.tertiaryContainer,
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
                    color = MaterialTheme.colorScheme.tertiary,
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Terminal,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiary,
                        )
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "CLI 项目工作流",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Text(
                        "使用mct project批量管理项目",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.tertiary,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Link,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onTertiary,
                        )
                        Text(
                            "CLI 原生",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onTertiary,
                        )
                    }
                }
            }
        }

        SectionTitle("项目位置", Icons.Outlined.FolderOpen)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PathRow(
                    label = "CLI 工作目录 / 项目根目录",
                    placeholder = "初始化前选父目录；已有项目选择含 mct.toml 的目录",
                    value = state.directory,
                    onValueChange = { onStateChange(state.copy(directory = it)) },
                    onBrowse = { projectDirectoryPicker.launch() },
                )
            }
        }

        SectionTitle("初始化参数", Icons.Outlined.CreateNewFolder)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val useTwoColumns = maxWidth >= 680.dp
            if (useTwoColumns) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProjectNameField(
                        value = state.name,
                        onValueChange = { onStateChange(state.copy(name = it)) },
                        modifier = Modifier.weight(1f),
                    )
                    Box(Modifier.weight(1f)) {
                        PathRow(
                            label = "源 Minecraft 存档",
                            placeholder = "选择包含 level.dat 的目录",
                            value = state.source,
                            onValueChange = { onStateChange(state.copy(source = it)) },
                            onBrowse = { sourceDirectoryPicker.launch() },
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProjectNameField(
                        value = state.name,
                        onValueChange = { onStateChange(state.copy(name = it)) },
                    )
                    PathRow(
                        label = "源 Minecraft 存档",
                        placeholder = "选择包含 level.dat 的目录",
                        value = state.source,
                        onValueChange = { onStateChange(state.copy(source = it)) },
                        onBrowse = { sourceDirectoryPicker.launch() },
                    )
                }
            }
        }

        SectionTitle("执行步骤", Icons.Outlined.AccountTree)
        val steps = listOf(
            ProjectStep(
                1, "Init",
                Icons.Outlined.CreateNewFolder,
                hasProjectDirectory && state.name.isNotBlank() && state.source.isNotBlank(),
                onInit,
            ),
            ProjectStep(
                2,
                "Update",
                Icons.Outlined.Update,
                hasProjectDirectory,
                onUpdate
            ),
            ProjectStep(
                3,
                "术语",
                Icons.Outlined.Spellcheck,
                hasProjectDirectory,
                onTerms
            ),
            ProjectStep(
                4,
                "翻译",
                Icons.Outlined.Translate,
                hasProjectDirectory,
                onTranslate
            ),
            ProjectStep(
                5,
                "Build",
                Icons.Outlined.Build,
                hasProjectDirectory,
                onBuild
            ),
        )

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = when {
                maxWidth >= 720.dp -> 3
                maxWidth >= 420.dp -> 2
                else -> 1
            }
            val spacing = 12.dp
            val cardWidth = (maxWidth - spacing * (columns - 1)) / columns
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                maxItemsInEachRow = columns,
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalArrangement = Arrangement.spacedBy(spacing),
            ) {
                steps.forEach { step ->
                    WorkflowStepCard(
                        step = step,
                        isRunning = isRunning,
                        modifier = Modifier.width(cardWidth),
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkflowStepCard(
    step: ProjectStep,
    isRunning: Boolean,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier.heightIn(min = 172.dp),
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
                    modifier = Modifier.size(40.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            step.number.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Icon(step.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(step.title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.weight(1f))
            FilledTonalButton(
                onClick = step.onClick,
                enabled = step.enabled && !isRunning,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("执行 ${step.title}")
            }
        }
    }
}

@Composable
private fun ProjectNameField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text("项目名称", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("CLI 将创建同名子目录") },
        )
    }
}
