package mct.gui.state

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*
import mct.Env
import mct.cli.cmd.project.AIConfig
import mct.cli.cmd.project.ProjectConfig
import mct.gui.model.*
import mct.gui.services.*
import mct.gui.util.revealInFileExplorer
import java.io.File

/**
 * The project workflow as a state holder: which projects were opened before, which one is open now,
 * which page of the function area is shown, and the project files behind those pages.
 *
 * Project commands themselves live in `mct.gui.services`, which runs the CLI in process, and every
 * project file is read and written in the CLI's own format through the CLI's own types. Nothing
 * about `mct project` is re-implemented here.
 */
class ProjectController(
    private val env: Env,
    private val scope: CoroutineScope,
    private val operations: OperationRunner,
    private val snackbar: SnackbarHostState,
) {
    /** Opened projects, most recent first, persisted to `~/.mct/projects.json`. */
    var history by mutableStateOf<List<ProjectHistoryEntry>>(emptyList())
        private set

    /** The open project; `null` means the overview page is showing. */
    var opened by mutableStateOf<ProjectHistoryEntry?>(null)
        private set

    var section by mutableStateOf(ProjectSection.Dashboard)
        private set

    /** `mct.toml` editor of the open project. */
    var editor by mutableStateOf<ProjectConfigEditor?>(null)
        private set

    /** `mappings.json` and `terms.json`; `missing.json` is read-only, so it is just a list. */
    val mappings = ProjectTableEditor(ProjectTextFile.Mappings)
    val terms = ProjectTableEditor(ProjectTextFile.Terms)

    var missing by mutableStateOf<List<ProjectTextEntry>>(emptyList())
        private set

    var isDataLoading by mutableStateOf(false)
        private set

    var dataError by mutableStateOf<String?>(null)
        private set

    /** The action currently running; the action bar collapses to this entry's cancel button. */
    var runningAction by mutableStateOf<ProjectAction?>(null)
        private set

    /**
     * True while a `mct project` command is running.
     *
     * Such a run rewrites `mct.toml`, `mappings.json` and `terms.json` when it ends, so anything
     * saved meanwhile is lost; the pages disable their editing affordances on this flag.
     */
    val isCommandRunning: Boolean
        get() = runningAction != null

    // ── new-project dialog ──────────────────────────────────────
    var initForm by mutableStateOf(ProjectInitForm())
        private set

    var isInitDialogVisible by mutableStateOf(false)
        private set

    var initError by mutableStateOf<String?>(null)
        private set

    /** Guards against an older load overwriting a newer one. */
    private var loadGeneration = 0L

    /** Guards [runningAction] against a cancelled run clearing its successor's state. */
    private var actionGeneration = 0L

    init {
        scope.launch { loadHistory() }
    }

    // ── history ─────────────────────────────────────────────────
    private suspend fun loadHistory() {
        val stored = withContext(Dispatchers.IO) { projectHistorySetting.load() }
        history = stored.entries.sortedByDescending { it.lastOpenedAt }
    }

    private fun persistHistory() {
        val snapshot = ProjectHistory(history)
        scope.launch(Dispatchers.IO) { projectHistorySetting.save(snapshot) }
    }

    /** Move [entry] to the front of the history under its current timestamp. */
    private fun record(entry: ProjectHistoryEntry) {
        history = listOf(entry) + history.filterNot { it.directory == entry.directory }
        persistHistory()
    }

    /**
     * Open [entry]; [knownConfig] avoids reading `mct.toml` twice when the caller already read it
     * (the import flow needs the name from the file before it can open the project).
     */
    fun open(entry: ProjectHistoryEntry, knownConfig: ProjectConfig? = null) {
        // A running command belongs to the project it was started for, so switching projects ends it
        // instead of leaving its cancel button on another project's bar.
        stopRunningAction()
        val target = entry.copy(lastOpenedAt = System.currentTimeMillis())
        opened = target
        section = ProjectSection.Dashboard
        editor = ProjectConfigEditor(env, target.directory)
        mappings.applyLoaded(emptyList())
        terms.applyLoaded(emptyList())
        missing = emptyList()
        dataError = null
        record(target)
        refreshData(knownConfig = knownConfig)
    }

    fun close() {
        stopRunningAction()
        // A load still in flight belongs to the project being closed; without the bump it would
        // land its tables, `missing` and `dataError` on a controller that is no longer showing it.
        ++loadGeneration
        opened = null
        section = ProjectSection.Dashboard
        editor = null
        mappings.applyLoaded(emptyList())
        terms.applyLoaded(emptyList())
        missing = emptyList()
        dataError = null
        isDataLoading = false
    }

    private fun stopRunningAction() {
        if (runningAction == null) return
        operations.cancel()
        runningAction = null
    }

    fun forget(entry: ProjectHistoryEntry) {
        history = history.filterNot { it.directory == entry.directory }
        persistHistory()
        if (opened?.directory == entry.directory) close()
    }

    /**
     * Adopt a project that already exists on disk. The name shown comes from its own `mct.toml`,
     * and a directory without one is rejected instead of being remembered as a broken project.
     */
    fun importProject(directory: String) {
        scope.launch {
            val read = runCatching { with(env) { readProjectConfig(directory) } }
            val config = read.getOrNull()
            if (config == null) {
                val reason = read.exceptionOrNull()?.message.orEmpty()
                snackbar.showSnackbar(
                    if (File(directory, PROJECT_FILE).isFile) {
                        "无法导入：$PROJECT_FILE 解析失败（$reason）"
                    } else {
                        "无法导入：所选目录下没有 $PROJECT_FILE"
                    },
                )
                return@launch
            }
            val root = File(directory).absoluteFile
            val entry = ProjectHistoryEntry(
                name = config.name.ifBlank { root.name },
                directory = root.path,
            )
            open(entry, knownConfig = config)
            snackbar.showSnackbar("已导入项目「${entry.name}」")
        }
    }

    fun showSection(target: ProjectSection) {
        section = target
        // A failed load left the data pages empty; entering one retries instead of showing nothing.
        if (target != ProjectSection.Dashboard && dataError != null && !isDataLoading) refreshData()
    }

    // ── project data ────────────────────────────────────────────
    /**
     * Reload `mct.toml` and the three text files of the open project.
     *
     * All of them come from one pass so the pages and the function cards agree, and so the tables
     * are located with the paths of the configuration that was just read.
     *
     * With [preserveEdits] (the default) a file the user has edited but not saved keeps its working
     * copy: saving the configuration would otherwise throw away unsaved mapping edits. The reload
     * buttons pass `false`, which is the explicit "放弃未保存的修改" action.
     *
     * [knownConfig] is for callers that just read the file themselves.
     */
    fun refreshData(preserveEdits: Boolean = true, knownConfig: ProjectConfig? = null) {
        val target = opened ?: return
        val generation = ++loadGeneration
        isDataLoading = true
        scope.launch {
            val readConfig = runCatching { knownConfig ?: with(env) { readProjectConfig(target.directory) } }
            val config = readConfig.getOrNull()
            if (generation != loadGeneration) return@launch
            if (config == null) {
                // Without the configuration the tables cannot even be located.
                val message = readConfig.exceptionOrNull()?.message ?: "无法读取 $PROJECT_FILE"
                editor?.failLoad(message)
                dataError = message
                isDataLoading = false
                return@launch
            }

            // The three tables are independent files; reading them one after another made a large
            // mapping file delay the term and missing lists behind it for no reason.
            val readTables = runCatching {
                with(env) {
                    coroutineScope {
                        val mappingsJob = async { readProjectMappings(target.directory, config.mappings) }
                        val termsJob = async { readProjectTerms(target.directory, config.terms) }
                        val missingJob = async { readProjectMissing(target.directory) }
                        ProjectData(
                            config = config,
                            mappings = mappingsJob.await(),
                            mappingsPath = config.mappings,
                            terms = termsJob.await(),
                            termsPath = config.terms,
                            missing = missingJob.await(),
                        )
                    }
                }
            }
            if (generation != loadGeneration) return@launch

            // The configuration file itself is fine even when a table fails to load, so it still
            // replaces the editor; only the tables keep their previous contents in that case.
            val current = editor
            if (!preserveEdits || current?.isDirty != true) current?.applyLoaded(config)
            readTables.fold(
                onSuccess = { data ->
                    if (!preserveEdits || !mappings.isDirty) mappings.applyLoaded(data.mappings, data.mappingsPath)
                    if (!preserveEdits || !terms.isDirty) terms.applyLoaded(data.terms, data.termsPath)
                    missing = data.missing
                    dataError = null
                },
                onFailure = { error -> dataError = error.message ?: "读取项目文件失败" },
            )
            isDataLoading = false
        }
    }

    private data class ProjectData(
        val config: ProjectConfig,
        val mappings: List<ProjectTextEntry>,
        /** Path of each table as the configuration just read declares it. */
        val mappingsPath: String,
        val terms: List<ProjectTextEntry>,
        val termsPath: String,
        val missing: List<ProjectTextEntry>,
    )

    /** Entries of one text file, as the pages render them. */
    fun entriesOf(file: ProjectTextFile): List<ProjectTextEntry> = when (file) {
        ProjectTextFile.Mappings -> mappings.entries
        ProjectTextFile.Terms -> terms.entries
        ProjectTextFile.Missing -> missing
    }

    /** Editor of one text file, or `null` for the read-only `missing.json`. */
    fun editorOf(file: ProjectTextFile): ProjectTableEditor? = when (file) {
        ProjectTextFile.Mappings -> mappings
        ProjectTextFile.Terms -> terms
        ProjectTextFile.Missing -> null
    }

    /** The CLI refuses to run with a blank token or with the placeholder its default config carries. */
private fun String.isAiTokenUnset(): Boolean = isBlank() || this == AIConfig.Default.token

/** The path of one text file, as configured in `mct.toml` (or the CLI's default). */
    fun pathOf(file: ProjectTextFile): String = when (file) {
        ProjectTextFile.Mappings -> editor?.mappings?.value ?: file.defaultPath
        ProjectTextFile.Terms -> editor?.terms?.value ?: file.defaultPath
        ProjectTextFile.Missing -> PROJECT_MISSING_FILE
    }

    // ── saving ──────────────────────────────────────────────────
    /** Save the editor back to `mct.toml`, then reread everything the file points at. */
    fun saveConfig() {
        val current = editor ?: return
        if (refuseWhileCommandRuns("保存项目配置")) return
        scope.launch {
            current.save().fold(
                onSuccess = {
                    snackbar.showSnackbar("项目配置已保存")
                    // Only the saved file is replaced; anything the user is still editing stays.
                    refreshData()
                },
                onFailure = { snackbar.showSnackbar(it.message ?: "项目配置保存失败") },
            )
        }
    }

    /** Save one text table. */
    fun saveTable(file: ProjectTextFile) {
        val table = editorOf(file) ?: return
        if (refuseWhileCommandRuns("保存${file.title}")) return
        // The rows were read from `loadedPath`, but the path they would be written to comes from the
        // live editor — which may hold an unsaved change of that field. Writing the old rows to the
        // new file would replace a table the user never opened.
        val loadedFrom = table.loadedPath
        val configured = pathOf(file)
        if (loadedFrom != null && loadedFrom != configured) {
            scope.launch {
                snackbar.showSnackbar(
                    "「${file.title}」的路径已改为 $configured 但尚未保存配置；" +
                        "请先保存项目配置，再保存该表格",
                )
            }
            return
        }
        scope.launch {
            table.save { entries -> writeTable(file, entries) }.fold(
                onSuccess = { snackbar.showSnackbar("${file.title}已保存") },
                onFailure = { snackbar.showSnackbar(it.message ?: "${file.title}保存失败") },
            )
        }
    }

    /**
     * Whether an editing action must be refused because a `mct project` command is running.
     *
     * The CLI rewrites `mct.toml`, `mappings.json` and `terms.json` at the end of a run, so anything
     * saved meanwhile is silently replaced by the run's version; reporting why is better than
     * accepting a write that disappears. Returns `true` when [what] was refused.
     */
    private fun refuseWhileCommandRuns(what: String): Boolean {
        val running = runningAction ?: return false
        scope.launch {
            snackbar.showSnackbar("「${running.label}」正在运行，结束后才能$what")
        }
        return true
    }

    private suspend fun writeTable(file: ProjectTextFile, entries: List<ProjectTextEntry>) {
        // Throwing rather than returning quietly: a save that wrote nothing must not be reported as
        // "saved".
        val directory = requireNotNull(opened) { "项目已关闭" }.directory
        val config = requireNotNull(editor) { "项目已关闭" }
        val pretty = config.prettyJson.value
        when (file) {
            ProjectTextFile.Mappings -> with(env) {
                writeProjectMappings(directory, config.mappings.value, entries, pretty)
            }

            ProjectTextFile.Terms -> with(env) {
                writeProjectTerms(directory, config.terms.value, entries, pretty)
            }

            ProjectTextFile.Missing -> Unit
        }
    }

    /**
     * Write every edited file before a command runs, so the CLI acts on what is on screen rather
     * than on the last saved state. Returns false when something could not be written.
     */
    private suspend fun flushEditors(): Boolean {
        var ok = true
        editor?.takeIf { it.isDirty }?.save()?.onFailure {
            ok = false
            snackbar.showSnackbar(it.message ?: "项目配置保存失败")
        }
        for (table in listOf(mappings, terms)) {
            if (!table.isDirty) continue
            table.save { entries -> writeTable(table.file, entries) }.onFailure {
                ok = false
                snackbar.showSnackbar(it.message ?: "${table.file.title}保存失败")
            }
        }
        return ok
    }

    // ── commands ────────────────────────────────────────────────
    /**
     * Run one `mct project` subcommand for the open project, then reload the data pages: every
     * command can rewrite the mapping file, and `update` rewrites the missing pool.
     *
     * The command runs through [OperationRunner], so the action bar can turn into its cancel button
     * for as long as it lasts.
     */
    fun run(action: ProjectAction) {
        val directory = opened?.directory ?: return
        preflight(action)?.let { problem ->
            scope.launch { snackbar.showSnackbar(problem) }
            return
        }
        val generation = ++actionGeneration
        runningAction = action
        operations.launch {
            try {
                if (!flushEditors()) {
                    snackbar.showSnackbar("有文件未能保存，已取消「${action.label}」")
                    return@launch
                }
                with(env) {
                    when (action) {
                        ProjectAction.Update -> updateProject(directory)
                        ProjectAction.Term -> extractProjectTerms(directory)
                        ProjectAction.Translate -> translateProject(directory)
                        ProjectAction.Build -> buildProject(directory)
                        ProjectAction.Patch -> assembleProjectPatch(directory)
                    }
                }
            } finally {
                if (generation == actionGeneration) runningAction = null
                refreshData()
            }
        }
    }

    fun cancel() = operations.cancel()

    /**
     * The first reason [action] cannot run, or `null` when it can.
     *
     * The CLI reports most failures by printing and returning normally, which the GUI cannot tell
     * apart from success — so the prerequisites are checked here instead, and the user gets a
     * sentence they can act on rather than a button that seems to do nothing.
     */
    fun preflight(action: ProjectAction): String? {
        val directory = opened?.directory ?: return null
        val config = editor ?: return null
        val extracted = cachedExtractions(directory).isNotEmpty()
        return when {
            action != ProjectAction.Update && !extracted ->
                "还没有提取结果：请先运行「更新」"

            action == ProjectAction.Build || action == ProjectAction.Patch ->
                if (mappings.entries.isEmpty()) "还没有映射：请先运行「翻译」或手动添加" else null

            // `project term` reads `missing.json` itself, so an empty pool means there is nothing
            // to do. `project translate` works from the cached pool instead, so an empty (or stale)
            // missing list must not block it.
            action == ProjectAction.Term && missing.isEmpty() ->
                "没有待翻译的文本：映射已经覆盖了全部提取结果，或先运行「更新」"

            action == ProjectAction.Term || action == ProjectAction.Translate ->
                if (config.engineKind.value == ProjectEngineKind.Ai && config.aiToken.value.isAiTokenUnset()) {
                    "AI 未配置：请在项目配置里填写 ai.token，或把翻译引擎改为 API"
                } else {
                    null
                }

            else -> null
        }
    }

    /** Extraction caches the CLI writes; without one, its commands abort immediately. */
    private fun cachedExtractions(directory: String): List<File> =
        File(directory, "cache").listFiles()?.filter { it.isFile && it.extension == "json" }.orEmpty()

    // ── new project ─────────────────────────────────────────────
    fun showInitDialog() {
        initError = null
        isInitDialogVisible = true
    }

    fun hideInitDialog() {
        initError = null
        isInitDialogVisible = false
    }

    fun updateInitForm(transform: (ProjectInitForm) -> ProjectInitForm) {
        initForm = transform(initForm)
    }

    /** Create the project through `mct project init`, then open it. */
    fun initialise() {
        val form = initForm
        val error = when {
            form.directory.isBlank() -> "请选择 CLI 工作目录"
            else -> projectNameError(form.name)
                ?: if (form.source.isBlank()) "请选择源存档目录" else null
        }
        if (error != null) {
            initError = error
            return
        }
        initError = null
        operations.launch {
            val root = with(env) {
                initialiseProject(form.directory, form.name, form.source, form.engine)
            }
            isInitDialogVisible = false
            // Keep the working directory: creating a second project next to the first is common.
            initForm = ProjectInitForm(directory = form.directory)
            open(ProjectHistoryEntry(name = form.name.trim(), directory = root))
        }
    }

    // ── paths ───────────────────────────────────────────────────
    /**
     * Show [path] in the file manager.
     *
     * Most of a project is created by the CLI, so the interesting paths often do not exist yet;
     * handing a missing path to the file manager opens some unrelated default folder. The closest
     * existing ancestor is revealed instead, with a note saying which one it was.
     */
    fun revealPath(path: String) {
        if (path.isBlank()) return
        val target = File(path)
        val existing = existingAncestor(path)
        when {
            existing == null -> scope.launch { snackbar.showSnackbar("无法在资源管理器中打开: $path") }
            existing.path != target.path -> {
                reveal(existing.path)
                scope.launch { snackbar.showSnackbar("${target.name} 还不存在，已打开 ${existing.name}") }
            }

            else -> reveal(existing.path)
        }
    }

    /** The path itself, or its closest existing ancestor; `null` when nothing on the chain exists. */
    private fun existingAncestor(path: String): File? =
        generateSequence(File(path)) { it.parentFile }.firstOrNull { it.exists() }

    private fun reveal(path: String) {
        if (!revealInFileExplorer(path)) {
            scope.launch { snackbar.showSnackbar("无法在资源管理器中打开: $path") }
        }
    }

    /** Reveal a path of the open project, resolving project-relative paths like the CLI does. */
    fun revealProjectFile(path: String) {
        val directory = opened?.directory ?: return
        if (path.isBlank()) return
        revealPath(resolveProjectPath(directory, path).path)
    }
}
