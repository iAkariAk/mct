package mct.gui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import mct.gui.components.EnumButtonGroup
import mct.gui.components.PathRow
import mct.gui.model.ProjectTranslationEngine
import mct.gui.platform.platformPathOf
import mct.gui.state.ProjectController

/**
 * The "new project" dialog: the three inputs `mct project init` needs, plus the engine it should
 * record in `mct.toml`.
 *
 * It owns only the picking; the fields live in [ProjectController.initForm] and every action goes
 * back through the controller, so the dialog can be dismissed and reopened without losing what was
 * typed.
 *
 * [isRunning] disables both buttons: the CLI run has already started by the time the dialog would
 * close itself, and closing early would hide the console output the run reports through.
 */
@Composable
fun ProjectInitDialog(
    controller: ProjectController,
    isRunning: Boolean,
    onDismiss: () -> Unit,
) {
    val form = controller.initForm
    // Both paths are directories: `--project-dir` is where the project folder is created, and
    // `--from` is the world (or datapack) directory to copy.
    val directoryPicker = rememberDirectoryPickerLauncher { file: PlatformFile? ->
        file?.let { controller.updateInitForm { form -> form.copy(directory = platformPathOf(it)) } }
    }
    val sourcePicker = rememberDirectoryPickerLauncher { file: PlatformFile? ->
        file?.let { controller.updateInitForm { form -> form.copy(source = platformPathOf(it)) } }
    }
    AlertDialog(
        // The run is already in flight once it starts; allowing dismissal would hide its output.
        onDismissRequest = { if (!isRunning) onDismiss() },
        title = { Text("新建项目") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // The keyboard on a phone covers the lower half, and the error line sits at the
                    // bottom, so the form scrolls rather than being clipped.
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PathRow(
                    label = "CLI 工作目录",
                    placeholder = "项目将创建在该目录下",
                    value = form.directory,
                    onValueChange = { controller.updateInitForm { f -> f.copy(directory = it) } },
                    onBrowse = { directoryPicker.launch() },
                )
                PathRow(
                    label = "项目名称",
                    placeholder = "将成为目录名，不能包含路径分隔符",
                    value = form.name,
                    onValueChange = { controller.updateInitForm { f -> f.copy(name = it) } },
                    // A name is typed and must not exist yet, so there is nothing to resolve and
                    // nothing to pick.
                    mustExist = false,
                    onBrowse = null,
                )
                PathRow(
                    label = "源存档目录",
                    placeholder = "选择包含 level.dat 的文件夹",
                    value = form.source,
                    onValueChange = { controller.updateInitForm { f -> f.copy(source = it) } },
                    onBrowse = { sourcePicker.launch() },
                )
                Text(
                    "翻译方式",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                EnumButtonGroup(
                    entries = ProjectTranslationEngine.entries,
                    selected = form.engine,
                    label = { it.label },
                    onSelected = { controller.updateInitForm { f -> f.copy(engine = it) } },
                )
                controller.initError?.let { error ->
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = controller::initialise,
                enabled = !isRunning,
            ) { Text("创建") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isRunning) { Text("取消") }
        },
    )
}
