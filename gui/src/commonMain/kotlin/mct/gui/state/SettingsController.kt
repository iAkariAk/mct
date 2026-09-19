package mct.gui.state

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import mct.LoggerLevel
import mct.gui.model.GuiSettings
import mct.gui.model.LogEntry
import mct.gui.services.ApiSettings
import mct.gui.services.ThemeSettings
import mct.gui.services.apiSetting
import mct.gui.services.themeSetting
import kotlin.time.Duration
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
    private var lastSavedTheme: ThemeSettings? = null

    /**
     * The API-relevant slice of UI state that would be persisted.
     *
     * [derivedStateOf] caches the value: unrelated translate-panel edits recompute it but do not
     * invalidate observers, so auto-save keeps counting down instead of restarting on every
     * keystroke anywhere in the panel.
     */
    private val apiSnapshot = derivedStateOf { snapshot() }
    private val themeSnapshot = derivedStateOf { themeSnapshotOf() }

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
        prettyOutput = GuiSettings.prettyOutput,
        engine = translation.state.engine,
        api = translation.state.api,
    )

    private fun themeSnapshotOf() = ThemeSettings(
        seedColorArgb = GuiSettings.seedColorArgb,
        isDynamicThemeEnabled = GuiSettings.isDynamicThemeEnabled,
        isRainbowTheme = GuiSettings.isRainbowTheme,
    )

    /** Load settings from disk and apply them to UI state. */
    suspend fun load() = withContext(Dispatchers.IO) {
        val saved = apiSetting.loadOrNull()
        if (saved == null && apiSetting.exists()) {
            logs.add(LogEntry(LoggerLevel.Warning, "无法读取 ${apiSetting.path}，已使用默认 API 设置"))
        }
        val theme = themeSetting.loadOrNull()
        if (theme == null && themeSetting.exists()) {
            logs.add(LogEntry(LoggerLevel.Warning, "无法读取 ${themeSetting.path}，已使用默认主题设置"))
        }
        val api = saved ?: ApiSettings()
        val themeSettings = theme ?: ThemeSettings()
        withContext(Dispatchers.Main) {
            translation.state = translation.state.copy(
                apiUrl = api.apiUrl,
                model = api.model,
                apiToken = api.apiToken,
                engine = api.engine,
                api = api.api,
            )
            GuiSettings.temperature = api.temperature
            GuiSettings.useStreamApi = api.useStreamApi
            GuiSettings.tokenThreshold = api.tokenThreshold
            GuiSettings.concurrency = api.concurrency
            GuiSettings.concurrentByKind = api.concurrentByKind
            GuiSettings.prettyOutput = api.prettyOutput
            GuiSettings.seedColorArgb = themeSettings.seedColorArgb
            GuiSettings.isDynamicThemeEnabled = themeSettings.isDynamicThemeEnabled
            GuiSettings.isRainbowTheme = themeSettings.isRainbowTheme
            lastSaved = snapshot()
            lastSavedTheme = themeSnapshotOf()
            // Only when a file was really read: on a first run, or after a failed read reported
            // above, the values came from defaults and saying otherwise would be misleading.
            if (saved != null && (api.apiUrl.isNotBlank() || api.apiToken.isNotBlank())) {
                logs.add(LogEntry(null, "已加载 API 设置 (${apiSetting.path})"))
            }
        }
    }

    /**
     * Auto-save: write to disk once a snapshot has been stable for [debounce].
     *
     * Started from composition; the first snapshot is skipped so startup writes nothing.
     * [debounce] is a test seam: production always uses [AUTO_SAVE_DEBOUNCE].
     */
    @OptIn(FlowPreview::class)
    suspend fun autoSave(debounce: Duration = AUTO_SAVE_DEBOUNCE) {
        snapshotFlow { apiSnapshot.value to themeSnapshot.value }
            .drop(1)
            .distinctUntilChanged()
            .debounce(debounce)
            .collect {
                // The emission only says "something changed"; the payload is read at write time. A
                // debounced value can be older than an edit made just before the window was closed,
                // and writing that older snapshot after [flush] would undo the edit.
                val api = snapshot()
                val theme = themeSnapshotOf()
                if (!save(api)) {
                    logs.add(LogEntry(LoggerLevel.Warning, "自动保存 API 设置失败: ${apiSetting.path}"))
                }
                if (!saveTheme(theme)) {
                    logs.add(LogEntry(LoggerLevel.Warning, "自动保存主题设置失败: ${themeSetting.path}"))
                }
            }
    }

    /**
     * Write the pending snapshot now, without waiting for [AUTO_SAVE_DEBOUNCE].
     *
     * Called when the application is closing: an edit made just before the window is closed would
     * otherwise sit in the debounce window and never reach disk, which loses every setting the user
     * changed in the last few seconds.
     */
    suspend fun flush() {
        save(snapshot())
        saveTheme(themeSnapshotOf())
    }

    /** Serialises the api/theme writes, so a stale snapshot cannot land after a newer one. */
    private val writeLock = Mutex()

    /** Write [settings], skipping when it equals the last successfully written snapshot. */
    private suspend fun save(settings: ApiSettings): Boolean = writeLock.withLock {
        if (settings == lastSaved) return@withLock true
        val saved = withContext(Dispatchers.IO) { apiSetting.save(settings) }
        if (saved) lastSaved = settings
        saved
    }

    private suspend fun saveTheme(settings: ThemeSettings): Boolean = writeLock.withLock {
        if (settings == lastSavedTheme) return@withLock true
        val saved = withContext(Dispatchers.IO) { themeSetting.save(settings) }
        if (saved) lastSavedTheme = settings
        saved
    }
}
