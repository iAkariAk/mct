package mct.gui.state

import androidx.compose.runtime.*
import mct.Env
import mct.cli.cmd.project.*
import mct.gui.model.ProjectPatternPaths
import mct.gui.model.ProjectPatternSlot
import mct.gui.services.writeProjectConfig

/**
 * Editor over one project's `mct.toml`.
 *
 * Every field is exposed as its own [State] that is computed from the whole-cloth [config] snapshot.
 * That is what makes the editor cheap: a composable reads `editor.aiModel.value` inside its own
 * scope, so one keystroke invalidates that one field instead of the page. Handing the `ProjectConfig`
 * itself to the field composables would not work — it is an unstable data class, so every field
 * would recompose on every keystroke anywhere in the form.
 *
 * Writes go through [update] and friends, which replace the snapshot; the derived states then
 * notify only the fields whose value actually changed (`derivedStateOf` compares structurally).
 *
 * The type being edited is the CLI's own [ProjectConfig], and the file is parsed and rendered by the
 * CLI's Toml codec, so the GUI never grows a second opinion about `mct.toml`.
 */
class ProjectConfigEditor(
    private val env: Env,
    /** Project root, i.e. the directory holding `mct.toml`. */
    val directory: String,
) {
    private var config by mutableStateOf(ProjectConfig(name = ""))

    /** False until the file has been read; the page shows a placeholder instead of stale fields. */
    var isLoaded by mutableStateOf(false)
        private set

    /** Set when `mct.toml` could not be read (missing file, invalid TOML). */
    var loadError by mutableStateOf<String?>(null)
        private set

    /** True once a field changed without being written back to disk. */
    var isDirty by mutableStateOf(false)
        private set

    var isSaving by mutableStateOf(false)
        private set

    private fun <T> field(get: (ProjectConfig) -> T): State<T> = derivedStateOf { get(config) }

    // ── project ─────────────────────────────────────────────────
    val name = field { it.name }
    val version = field { it.version }
    val description = field { it.description }

    // ── files ───────────────────────────────────────────────────
    val mappings = field { it.mappings }
    val mtlx = field { it.mtlx }
    val terms = field { it.terms }
    val prettyJson = field { it.prettyJson }

    /** One editor per extraction-pattern category, in [ProjectPatternSlot] order. */
    val patternSlots: List<ProjectPatternSlotEditor> = ProjectPatternSlot.entries.map { slot ->
        ProjectPatternSlotEditor(
            slot = slot,
            value = field { it.patterns.toPaths(slot) },
            write = { paths -> update { it.copy(patterns = it.patterns.withPaths(slot, paths)) } },
        )
    }

    // ── map info ────────────────────────────────────────────────
    val mapInfo = field { it.mapInfo }

    // ── ai translation ──────────────────────────────────────────
    val aiApiUrl = field { it.ai.apiUrl }
    val aiToken = field { it.ai.token }
    val aiModel = field { it.ai.model }
    val aiUseStreamApi = field { it.ai.useStreamApi }
    val aiTokenThreshold = field { it.ai.tokenThreshold }
    val aiLiteratureStyle = field { it.ai.literatureStyle }
    val aiTargetLanguage = field { it.ai.targetLanguage }
    val aiExtraPrompts = field { it.ai.extraPrompts }
    val aiTemperature = field { it.ai.temperature }
    val aiHandleGradient = field { it.ai.handleGradientAggressively }
    val aiHttpLogging = field { it.ai.enableHttpLogging }
    val aiThinkingOutput = field { it.ai.enableThinkingOutput }

    // ── translation engine ──────────────────────────────────────
    val engineKind = field { ProjectEngineKind.of(it.translation.engine) }
    val apiUrl = field {
        (it.translation.engine as? TranslationEngine.Api.MTranServer)?.apiUrl
            ?: TranslationEngine.Api.MTranServer.Default.apiUrl
    }
    val apiToken = field { (it.translation.engine as? TranslationEngine.Api.MTranServer)?.token }
    val apiMaxRetry = field {
        (it.translation.engine as? TranslationEngine.Api)?.config?.maxRetry
            ?: TranslationEngine.Api.ApiConfig.Default.maxRetry
    }
    val apiSource = field { (it.translation.engine as? TranslationEngine.Api)?.config?.source }
    val apiTarget = field {
        (it.translation.engine as? TranslationEngine.Api)?.config?.target
            ?: TranslationEngine.Api.ApiConfig.Default.target
    }
    val concurrency = field { it.translation.concurrency }
    val concurrentByKind = field { it.translation.concurrentByKind }

    // ── patch ───────────────────────────────────────────────────
    val patchName = field { it.patch.name }
    val patchKind = field { it.patch.kind }

    // ── edits ───────────────────────────────────────────────────
    /** Replace the snapshot; every field subscription is notified only if its own value changed. */
    fun update(transform: (ProjectConfig) -> ProjectConfig) {
        config = transform(config)
        isDirty = true
    }

    fun updateAi(transform: (AIConfig) -> AIConfig) = update { it.copy(ai = transform(it.ai)) }

    fun updateTranslation(transform: (TranslationConfig) -> TranslationConfig) =
        update { it.copy(translation = transform(it.translation)) }

    fun updatePatch(transform: (PatchConfig) -> PatchConfig) = update { it.copy(patch = transform(it.patch)) }

    /**
     * Switch between the CLI's two engines, keeping the API settings already entered when the user
     * goes back to AI and returns.
     */
    fun selectEngineKind(kind: ProjectEngineKind) = updateTranslation {
        val engine = when (kind) {
            ProjectEngineKind.Ai -> TranslationEngine.AI
            ProjectEngineKind.Api -> it.engine as? TranslationEngine.Api ?: TranslationEngine.Api.MTranServer.Default
        }
        it.copy(engine = engine)
    }

    /** API settings of the current `translation.engine`; a no-op while the engine is AI. */
    private fun updateApi(transform: (TranslationEngine.Api) -> TranslationEngine.Api) = updateTranslation {
        val api = it.engine as? TranslationEngine.Api ?: return@updateTranslation it
        it.copy(engine = transform(api))
    }

    fun setApiUrl(value: String) = updateApi { it.withUrl(value) }
    fun setApiToken(value: String?) = updateApi { it.withToken(value) }
    fun updateApiConfig(transform: (TranslationEngine.Api.ApiConfig) -> TranslationEngine.Api.ApiConfig) =
        updateApi { it.withConfig(transform(it.config)) }

    // ── load / save ─────────────────────────────────────────────
    /** Adopt the [loaded] configuration; called by the controller once the file has been read. */
    fun applyLoaded(loaded: ProjectConfig) {
        config = loaded
        loadError = null
        isLoaded = true
        isDirty = false
    }

    /** Report that `mct.toml` could not be read; the page offers a retry. */
    fun failLoad(message: String) {
        isLoaded = false
        loadError = message
    }

    /** Write the edited configuration back to `mct.toml`. */
    suspend fun save(): Result<Unit> {
        isSaving = true
        return runCatching { with(env) { writeProjectConfig(directory, config) } }
            .onSuccess { isDirty = false }
            .also { isSaving = false }
    }
}

/** One extraction-pattern category of the editor: its path list and its built-in switch. */
class ProjectPatternSlotEditor internal constructor(
    val slot: ProjectPatternSlot,
    private val value: State<ProjectPatternPaths>,
    private val write: (ProjectPatternPaths) -> Unit,
) {
    /** Paths of the category, in file order. */
    val paths: State<List<String>> = derivedStateOf { value.value.paths }

    /** Whether the CLI's built-in patterns for this category are merged in (`has_builtin`). */
    val hasBuiltin: State<Boolean> = derivedStateOf { value.value.hasBuiltin }

    fun addPath(path: String) {
        val current = value.value.paths
        if (path.isBlank() || path in current) return
        write(value.value.copy(paths = current + path))
    }

    fun removePath(path: String) = write(value.value.copy(paths = value.value.paths - path))

    fun setHasBuiltin(enabled: Boolean) = write(value.value.copy(hasBuiltin = enabled))

    fun replacePaths(paths: List<String>) = write(value.value.copy(paths = paths))
}

/** Which engine `translation.engine` of `mct.toml` holds. */
enum class ProjectEngineKind(val label: String) {
    Ai("AI"),
    Api("API（MTranServer）");

    companion object {
        fun of(engine: TranslationEngine): ProjectEngineKind = when (engine) {
            TranslationEngine.AI -> Ai
            is TranslationEngine.Api -> Api
        }
    }
}

// ── mct.toml shape adapters ───────────────────────────────────
// The CLI stores pattern categories in three shapes; these map them onto one.

private fun PatternsConfig.toPaths(slot: ProjectPatternSlot): ProjectPatternPaths = when (slot) {
    ProjectPatternSlot.Nbt -> nbt.toPaths()
    ProjectPatternSlot.McJson -> mcjson.toPaths()
    ProjectPatternSlot.Command -> command.toPaths()
    ProjectPatternSlot.CommandData -> commandData.toPaths()
    ProjectPatternSlot.CommandComponent -> commandComponent.toPaths()
    ProjectPatternSlot.CommandRegex -> ProjectPatternPaths(commandRegex.toList(), hasBuiltin = false)
    ProjectPatternSlot.Cext -> ProjectPatternPaths(cext, hasBuiltin = false)
}

private fun PatternsConfig.withPaths(slot: ProjectPatternSlot, paths: ProjectPatternPaths): PatternsConfig = when (slot) {
    ProjectPatternSlot.Nbt -> copy(nbt = paths.toPatternWithBuiltin())
    ProjectPatternSlot.McJson -> copy(mcjson = paths.toPatternWithBuiltin())
    ProjectPatternSlot.Command -> copy(command = paths.toPatternWithBuiltin())
    ProjectPatternSlot.CommandData -> copy(commandData = paths.toPatternWithBuiltin())
    ProjectPatternSlot.CommandComponent -> copy(commandComponent = paths.toPatternWithBuiltin())
    ProjectPatternSlot.CommandRegex -> copy(commandRegex = paths.paths.toSet())
    ProjectPatternSlot.Cext -> copy(cext = paths.paths)
}

private fun PatternWithBuiltin<Set<String>>.toPaths() = ProjectPatternPaths(patterns.toList(), hasBuiltin)

private fun ProjectPatternPaths.toPatternWithBuiltin() = PatternWithBuiltin(paths.toSet(), hasBuiltin)

// ── translation engine shape adapters ─────────────────────────

private fun TranslationEngine.Api.withUrl(url: String): TranslationEngine.Api = when (this) {
    is TranslationEngine.Api.MTranServer -> copy(apiUrl = url)
}

private fun TranslationEngine.Api.withToken(token: String?): TranslationEngine.Api = when (this) {
    is TranslationEngine.Api.MTranServer -> copy(token = token)
}

private fun TranslationEngine.Api.withConfig(
    config: TranslationEngine.Api.ApiConfig,
): TranslationEngine.Api = when (this) {
    is TranslationEngine.Api.MTranServer -> copy(config = config)
}
