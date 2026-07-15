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
import mct.gui.components.ConfigTextField
import mct.gui.components.SectionTitle
import mct.gui.model.ProjectWorkflowState

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
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            tonalElevation = 2.dp,
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("项目工作流", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text(
                    "配置一次项目路径，再按步骤执行初始化、翻译与构建。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }

        SectionTitle("项目路径", Icons.Outlined.FolderOpen)
        ConfigTextField(
            value = state.name,
            onValueChange = { onStateChange(state.copy(name = it)) },
            label = { Text("项目名称（可选）") },
            placeholder = { Text("留空则使用项目目录名称") },
        )
        ConfigTextField(
            value = state.directory,
            onValueChange = { onStateChange(state.copy(directory = it)) },
            label = { Text("项目目录") },
            placeholder = { Text("工作流配置与项目文件所在目录") },
        )
        ConfigTextField(
            value = state.source,
            onValueChange = { onStateChange(state.copy(source = it)) },
            label = { Text("源存档目录") },
            placeholder = { Text("待提取或构建的 Minecraft 存档目录") },
        )
        ConfigTextField(
            value = state.target,
            onValueChange = { onStateChange(state.copy(target = it)) },
            label = { Text("构建输出目录") },
            placeholder = { Text("最终 datapack 或发布文件输出目录") },
        )

        SectionTitle("执行步骤", Icons.Outlined.ImportExport)
        WorkflowStepCard(1, "Init", "创建项目结构与默认配置", Icons.Outlined.FolderOpen, isRunning, onInit)
        WorkflowStepCard(2, "Update", "同步源存档与项目资源", Icons.Outlined.Update, isRunning, onUpdate)
        WorkflowStepCard(3, "术语", "提取或更新项目术语表", Icons.Outlined.Translate, isRunning, onTerms)
        WorkflowStepCard(4, "翻译", "执行项目文本翻译与映射", Icons.Outlined.Translate, isRunning, onTranslate)
        WorkflowStepCard(5, "Build", "生成可交付的构建输出", Icons.Outlined.Build, isRunning, onBuild)
    }
}

@Composable
private fun WorkflowStepCard(
    number: Int,
    title: String,
    description: String,
    icon: ImageVector,
    isRunning: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(number.toString(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(title, style = MaterialTheme.typography.titleMedium)
                }
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalButton(onClick = onClick, enabled = !isRunning, shape = MaterialTheme.shapes.medium) {
                Text("执行")
            }
        }
    }
}
