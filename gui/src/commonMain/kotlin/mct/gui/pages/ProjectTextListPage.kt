@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package mct.gui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mct.gui.components.HoverHint
import mct.gui.model.ProjectSection
import mct.gui.model.ProjectTextEntry
import mct.gui.model.ProjectTextFile
import mct.gui.state.ProjectController
import mct.gui.state.ProjectTableEditor

/**
 * One text file of the project as a list: `mappings.json`, `missing.json` or `terms.json`.
 *
 * The editable files share this page with the read-only one — same rows, same search, and editing
 * affordances appear only when the file is editable: a FAB to add an entry, a swipe to delete one
 * and a tap to change one. Entries are edited in memory and written by the save button, exactly like
 * `mct.toml`.
 */
@Composable
fun ProjectTextListSection(
    controller: ProjectController,
    file: ProjectTextFile,
    modifier: Modifier = Modifier,
) {
    val entries = controller.entriesOf(file)
    val table = controller.editorOf(file)
    val path = controller.pathOf(file)
    // A running `mct project` command rewrites the tables at its end, so editing is frozen while it
    // lasts: anything saved now would be replaced by the run's own version.
    val editing = table != null && !controller.isCommandRunning
    // Reset the query per file: the pages are different lists with different filters.
    var query by remember(file) { mutableStateOf("") }
    var edit by remember(file) { mutableStateOf<EntryEdit?>(null) }
    val filtered = remember(query, entries) { filterEntries(entries, query) }

    Column(modifier = modifier.fillMaxSize()) {
        TextListHeader(
            controller = controller,
            file = file,
            path = path,
            total = entries.size,
            shown = filtered.size,
            query = query,
            onQueryChange = { query = it },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(Modifier.fillMaxSize()) {
            when {
                controller.isDataLoading && entries.isEmpty() -> ListPlaceholder { LoadingIndicator() }
                entries.isEmpty() -> EmptyTextList(file, editable = table != null)
                filtered.isEmpty() -> ListPlaceholder {
                    Text(
                        "没有匹配「$query」的条目",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> LazyColumn(
                    // Rows, not cards: each entry is a source line and its translation, and the
                    // source text is what sets the row's natural width.
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 96.dp),
                ) {
                    itemsIndexed(
                        items = filtered,
                        key = { _, entry -> entry.source },
                    ) { _, entry ->
                        TextEntryRow(
                            entry = entry,
                            file = file,
                            canEdit = editing,
                            onEdit = { edit = EntryEdit(entry.source, entry.source, entry.target.orEmpty()) },
                            onDelete = { table?.remove(entry.source) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }

            if (editing) {
                Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                    HoverHint("添加一条${file.title}") {
                        FloatingActionButton(
                            onClick = { edit = EntryEdit(null, "", "") },
                            shape = FloatingActionButtonDefaults.largeShape,
                        ) {
                            Icon(
                                Icons.Outlined.Add,
                                contentDescription = "添加一条${file.title}",
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    edit?.let { current ->
        EntryDialog(
            file = file,
            edit = current,
            onDismiss = { edit = null },
            onConfirm = { source, target ->
                val result = runCatching {
                    if (current.previousSource == null) {
                        table?.add(source, target)
                    } else {
                        table?.update(current.previousSource, source, target)
                    }
                }
                result.exceptionOrNull()?.message
            },
        )
    }
}

/** One open entry editor: no [previousSource] means the dialog is adding a new entry. */
private data class EntryEdit(val previousSource: String?, val source: String, val target: String)

@Composable
private fun TextListHeader(
    controller: ProjectController,
    file: ProjectTextFile,
    path: String,
    total: Int,
    shown: Int,
    query: String,
    onQueryChange: (String) -> Unit,
) {
    val table = controller.editorOf(file)
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TextListHeaderTitle(controller, file, path, total, shown, query, table)
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("搜索原文或译文") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Outlined.Close, contentDescription = "清除搜索", modifier = Modifier.size(18.dp))
                    }
                }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        )
    }
}

/**
 * Title, counters and the file's actions.
 *
 * A [FlowRow]: the title block keeps the row's remaining width, and the counters and buttons wrap
 * onto a second line when the window is too narrow for all of them instead of squeezing the title
 * into an ellipsis.
 */
@Composable
private fun TextListHeaderTitle(
    controller: ProjectController,
    file: ProjectTextFile,
    path: String,
    total: Int,
    shown: Int,
    query: String,
    table: ProjectTableEditor?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Title on its own line: it names the file, it is the widest text on the page, and sharing
        // a row with the counters squeezed it to an ellipsis on a narrow window.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HoverHint("返回功能区") {
                IconButton(onClick = { controller.showSection(ProjectSection.Dashboard) }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回功能区")
                }
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        file.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (table?.isDirty == true) {
                        UnsavedChip()
                    }
                }
                Text(
                    "$path — ${file.producer}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Counters and actions on their own line, pinned to the trailing edge and wrapping there:
        // four actions plus the count chip never fit beside the title at 500dp, and a wrapped action
        // is still reachable while a clipped one is not.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            CountChip(if (query.isBlank()) "$total 条" else "$shown / $total 条")
            // Only the read-only page needs a reload button: on the editable ones the workspace
            // header reloads without discarding edits, and the undo button below discards them.
            if (table == null) {
                HoverHint("重新读取项目文件（放弃未保存的修改）") {
                    IconButton(
                        onClick = { controller.refreshData(preserveEdits = false) },
                        enabled = !controller.isDataLoading,
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新", modifier = Modifier.size(20.dp))
                    }
                }
            }
            HoverHint("在资源管理器中打开 $path") {
                IconButton(onClick = { controller.revealProjectFile(path) }) {
                    Icon(Icons.Outlined.FolderOpen, contentDescription = "打开 $path", modifier = Modifier.size(20.dp))
                }
            }
            if (table != null) {
                HoverHint("放弃未保存的修改，重新读取文件") {
                    IconButton(
                        onClick = { controller.refreshData(preserveEdits = false) },
                        enabled = table.isDirty && !table.isSaving && !controller.isCommandRunning,
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "放弃修改", modifier = Modifier.size(20.dp))
                    }
                }
                Button(
                    onClick = { controller.saveTable(file) },
                    enabled = table.isDirty && !table.isSaving && !controller.isCommandRunning,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    if (table.isSaving) {
                        LoadingIndicator(modifier = Modifier.size(18.dp), color = LocalContentColor.current)
                    } else {
                        Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("保存")
                }
            }
        }
    }
}

@Composable
internal fun UnsavedChip() {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Text(
            "未保存",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun CountChip(text: String) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/**
 * A row of the list. When the entry can be edited the row is tappable and wrapped in a swipe box
 * that removes it, which is the Material treatment for "edit this row" and "throw this row away".
 */
@Composable
private fun TextEntryRow(
    entry: ProjectTextEntry,
    file: ProjectTextFile,
    canEdit: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!canEdit) {
        TextEntryRowContent(entry, file, canEdit = false, onEdit = onEdit, modifier = modifier)
        return
    }
    SwipeToDismissBox(
        state = rememberSwipeToDismissBoxState(),
        modifier = modifier,
        // Only the end-to-start direction is enabled, so settling anywhere dismissed means deleted.
        enableDismissFromStartToEnd = false,
        onDismiss = { value -> if (value == SwipeToDismissBoxValue.EndToStart) onDelete() },
        backgroundContent = {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "删除该条目",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        // The modifier stays on the swipe box; applying it again inside would double the item's
        // placement animation.
        TextEntryRowContent(entry, file, canEdit = true, onEdit = onEdit)
    }
}

@Composable
private fun TextEntryRowContent(
    entry: ProjectTextEntry,
    file: ProjectTextFile,
    canEdit: Boolean,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val translated = entry.target != null
    // A `null` translation is only an unfinished state in the missing pool; in the mapping it is a
    // deliberate "keep the original", so the row must not read as an error there.
    val keepOriginal = !translated && file == ProjectTextFile.Mappings
    ListItem(
        modifier = modifier.clickable(enabled = canEdit, onClick = onEdit),
        // Opaque on purpose: the swipe background lives behind the row, so a transparent row would
        // tint every swipeable entry with the delete colour.
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        leadingContent = {
            Icon(
                when {
                    translated -> Icons.Outlined.CheckCircle
                    keepOriginal -> Icons.Outlined.RemoveCircleOutline
                    else -> Icons.Outlined.ErrorOutline
                },
                contentDescription = entry.target ?: file.emptyValueLabel,
                tint = when {
                    translated -> MaterialTheme.colorScheme.primary
                    keepOriginal -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.error
                },
                modifier = Modifier.size(20.dp),
            )
        },
        headlineContent = {
            Text(
                entry.source,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                entry.target ?: file.emptyValueLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    translated -> MaterialTheme.colorScheme.onSurfaceVariant
                    keepOriginal -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.error
                },
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

/** Add / change dialog for one table entry. Returns an error message to show, or `null`. */
@Composable
private fun EntryDialog(
    file: ProjectTextFile,
    edit: EntryEdit,
    onDismiss: () -> Unit,
    onConfirm: (source: String, target: String) -> String?,
) {
    var source by remember { mutableStateOf(edit.source) }
    var target by remember { mutableStateOf(edit.target) }
    var error by remember { mutableStateOf<String?>(null) }
    val adding = edit.previousSource == null
    // Terms always carry a translation; a mapping entry without one is a valid "not translated yet".
    val targetRequired = file == ProjectTextFile.Terms

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Text(
                if (adding) "添加${file.title}" else "修改${file.title}",
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    minLines = 2,
                    label = { Text("原文 / 术语") },
                    supportingText = { Text("作为键写回 ${file.defaultPath}") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                )
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    minLines = 2,
                    label = { Text("译文") },
                    supportingText = {
                        Text(
                            when {
                                targetRequired -> "术语必须填写译文"
                                target.isBlank() -> "留空 = 保留原文：该文本不会被替换，也不会出现在缺失列表中"
                                else -> "写回 ${file.defaultPath}"
                            }
                        )
                    },
                    isError = error != null,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                )
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val failure = onConfirm(source, target)
                    if (failure == null) onDismiss() else error = failure
                },
                enabled = source.isNotBlank() && (!targetRequired || target.isNotBlank()),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(if (adding) "添加" else "保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun ListPlaceholder(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun EmptyTextList(file: ProjectTextFile, editable: Boolean) {
    ListPlaceholder {
        Icon(
            if (file == ProjectTextFile.Missing) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            "还没有${file.title}",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            if (editable) {
                "用右下角的 + 手动添加一条，或让 CLI 生成：${file.producer}"
            } else {
                "运行 mct project update 之后，这里会列出还没有译文的文本。"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Entries whose source or translation contains [query], case-insensitively. */
private fun filterEntries(entries: List<ProjectTextEntry>, query: String): List<ProjectTextEntry> {
    val needle = query.trim()
    if (needle.isEmpty()) return entries
    return entries.filter { entry ->
        entry.source.contains(needle, ignoreCase = true) ||
            entry.target?.contains(needle, ignoreCase = true) == true
    }
}
