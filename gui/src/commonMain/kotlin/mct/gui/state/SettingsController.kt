package mct.gui.state

import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.withContext
import mct.LoggerLevel
import mct.gui.model.GuiSettings
import mct.gui.model.LogEntry
import mct.gui.services.ApiSettings
import mct.gui.services.apiSetting
import mct.gui.services.themeSetting
import kotlin.time.Duration.Companion.seconds

/** Debounce window for auto-saving settings: write this long after editing stops. */
private val AUTO_SAVE_DEBOUNCE = 3.seconds

/**
 * Reads and writes the persisted UI fields under `~/.mct/`.
 *
 * This is where the boundary sits: disk IO, deduplication against the last written snapshot,
 * and debouncing after editing stops. Other modules only touch settings through [snapshot],
 * [load] and [autoSave].
 */
class SettingsController(
    private val logs: LogConsoleState,
    private val translation: TranslationController,
) {
    /** Last snapshot successfully written to disk; guards no-op auto-saves. */
    private var lastSaved: ApiSettings? = null

    /** The snapshot of current UI state that would be persisted. */
    fun snapshot() = ApiSettings(
        apiUrl = translation.state.apiUrl,
        model = translation.state.model,
        apiToken = translation.state.apiToken,
        useStreamApi = GuiSettings.useStreamApi,
        tokenThreshold = GuiSettings.tokenThreshold,
        temperature = GuiSettings.temperature,
        concurrency = GuiSettings.concurrency,
        concurrentByKind = GuiSettings.concurrentByKind,
        engine = translation.state.engine,
        api = translation.state.api,
    )

    /** Load settings from disk and apply them to UI state. */
    suspend fun load() = withContext(Dispatchers.IO) {
        val saved = apiSetting.load()
        val theme = themeSetting.load()
        withContext(Dispatchers.Main) {
            translation.state = translation.state.copy(
                apiUrl = saved.apiUrl,
                model = saved.model,
                apiToken = saved.apiToken,
                engine = saved.engine,
                api = saved.api,
            )
            GuiSettings.temperature = saved.temperature
            GuiSettings.useStreamApi = saved.useStreamApi
            GuiSettings.tokenThreshold = saved.tokenThreshold
            GuiSettings.concurrency = saved.concurrency
            GuiSettings.concurrentByKind = saved.concurrentByKind
            GuiSettings.seedColorArgb = theme.seedColorArgb
            if (theme.seedColorArgb != 0) GuiSettings.isDynamicThemeEnabled = true
            lastSaved = snapshot()
            if (saved.apiUrl.isNotBlank() || saved.apiToken.isNotBlank()) {
                logs.add(LogEntry(null, "已加载 API 设置 (${apiSetting.path})"))
            }
        }
    }

    /**
     * Auto-save: write to disk once [snapshot] has been stable for [AUTO_SAVE_DEBOUNCE].
     *
     * Started from composition; the first snapshot is skipped so startup writes nothing.
     */
    @OptIn(FlowPreview::class)
    suspend fun autoSave() {
        snapshotFlow { snapshot() }
            .drop(1)
            .distinctUntilChanged()
            .debounce(AUTO_SAVE_DEBOUNCE)
            .collect { settings ->
                if (!save(settings)) {
                    logs.add(LogEntry(LoggerLevel.Warning, "自动保存设置失败: ${apiSetting.path}"))
                }
            }
    }

    /** Write [settings], skipping when it equals the last successfully written snapshot. */
    private suspend fun save(settings: ApiSettings): Boolean {
        if (settings == lastSaved) return true
        val saved = withContext(Dispatchers.IO) { apiSetting.save(settings) }
        if (saved) lastSaved = settings
        return saved
    }
}
