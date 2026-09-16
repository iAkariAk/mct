@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package mct.gui.pages

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import mct.gui.components.EnumButtonGroup
import mct.gui.components.HoverHint
import mct.gui.components.PathRow
import mct.gui.model.ProjectHistoryEntry
import mct.gui.model.ProjectTranslationEngine
import mct.gui.services.projectNameError
import mct.gui.state.ProjectController

/**
 * The project tab: the project overview while nothing is open, the workspace once a project is,
 * plus the "new project" dialog floating above both.
 */
@Composable
fun ProjectPanel(
    controller: ProjectController,
    isRunning: Boolean,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = controller.opened,
            // Keyed by directory so switching between two projects animates instead of mutating
            // the open one's page in place.
            contentKey = { it?.directory },
            transitionSpec = {
                // Opening a project moves forward, returning to the list moves back.
                val forward = targetState != null
                val enter = slideInHorizontally(
                    animationSpec = motionScheme.defaultSpatialSpec(),
                    initialOffsetX = { width -> if (forward) width else -width },
                ) + fadeIn(animationSpec = motionScheme.defaultEffectsSpec())
                val exit = slideOutHorizontally(
                    animationSpec = motionScheme.fastSpatialSpec(),
                    targetOffsetX = { width -> if (forward) -width else width },
                ) + fadeOut(animationSpec = motionScheme.fastEffectsSpec())
                enter togetherWith exit
            },
            modifier = Modifier.fillMaxSize(),
            label = "project-page",
        ) { project: ProjectHistoryEntry? ->
            if (project == null) {
                ProjectOverviewPage(controller, isRunning)
            } else {
                ProjectWorkspacePage(controller, project, isRunning)
            }
        }
        ProjectInitDialog(
            controller = controller,
            isRunning = isRunning,
            visible = controller.isInitDialogVisible,
        )
    }
}

/** Modal for `mct project init`: parent directory, name, source world and translation engine. */
@Composable
private fun ProjectInitDialog(
    controller: ProjectController,
    isRunning: Boolean,
    visible: Boolean,
) {
    val motionScheme = MaterialTheme.motionScheme
    val form = controller.initForm
    val nameError = projectNameError(form.name).takeIf { form.name.isNotBlank() }
    val directoryPicker = rememberDirectoryPickerLauncher { file: PlatformFile? ->
        file?.let { picked -> controller.updateInitForm { it.copy(directory = picked.absolutePath()) } }
    }
    val sourcePicker = rememberDirectoryPickerLauncher { file: PlatformFile? ->
        file?.let { picked -> controller.updateInitForm { it.copy(source = picked.absolutePath()) } }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = motionScheme.defaultEffectsSpec()),
        exit = fadeOut(animationSpec = motionScheme.fastEffectsSpec()),
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { if (!isRunning) controller.hideInitDialog() }
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(max = 520.dp)
                    .padding(24.dp)
                    .animateEnterExit(
                        enter = scaleIn(
                            animationSpec = motionScheme.defaultSpatialSpec(),
                            initialScale = 0.92f,
                        ) + fadeIn(animationSpec = motionScheme.defaultEffectsSpec()),
                        exit = scaleOut(
                            animationSpec = motionScheme.fastSpatialSpec(),
                            targetScale = 0.92f,
                        ) + fadeOut(animationSpec = motionScheme.fastEffectsSpec()),
                    ),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 3.dp,
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Outlined.CreateNewFolder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text("新建项目", style = MaterialTheme.typography.headlineSmall)
                            Text(
                                "调用 mct project init：创建项目目录、复制源存档并生成 mct.toml。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HoverHint("关闭") {
                            IconButton(onClick = controller::hideInitDialog, enabled = !isRunning) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = "关闭",
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }

                    PathRow(
                        label = "CLI 工作目录",
                        placeholder = "项目将创建在该目录下的同名子目录中",
                        value = form.directory,
                        onValueChange = { value -> controller.updateInitForm { it.copy(directory = value) } },
                        onBrowse = { directoryPicker.launch() },
                    )

                    Column {
                        Text("项目名称", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = form.name,
                            onValueChange = { value -> controller.updateInitForm { it.copy(name = value) } },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = nameError != null,
                            placeholder = { Text("例如：MyMap（CLI 据此在工作目录下建同名子目录）") },
                            supportingText = nameError?.let { message -> { Text(message) } },
                        )
                    }

                    PathRow(
                        label = "源 Minecraft 存档",
                        placeholder = "选择包含 level.dat 的目录",
                        value = form.source,
                        onValueChange = { value -> controller.updateInitForm { it.copy(source = value) } },
                        onBrowse = { sourcePicker.launch() },
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HoverHint("mct project init --translation-engine：写入 mct.toml 的 [translation.engine]") {
                            Text("翻译引擎", style = MaterialTheme.typography.labelMedium)
                        }
                        EnumButtonGroup(
                            entries = ProjectTranslationEngine.entries,
                            selected = form.engine,
                            label = { it.label },
                            onSelected = { engine -> controller.updateInitForm { it.copy(engine = engine) } },
                        )
                    }

                    controller.initError?.let { message ->
                        Text(
                            message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(
                            onClick = controller::hideInitDialog,
                            enabled = !isRunning,
                            modifier = Modifier.weight(1f),
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text("取消")
                        }
                        Button(
                            onClick = controller::initialise,
                            enabled = !isRunning,
                            modifier = Modifier.weight(1f),
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            if (isRunning) {
                                LoadingIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = LocalContentColor.current,
                                )
                            } else {
                                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(if (isRunning) "创建中..." else "创建项目")
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            Icons.Outlined.Save,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "创建过程会在下方控制台输出 CLI 日志",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
