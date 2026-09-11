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

/** 设置自动保存的防抖窗口：停止编辑该时长后写入。 */
private val AUTO_SAVE_DEBOUNCE = 3.seconds

/**
 * 把界面状态里的持久字段读写到 `~/.mct/`。
 *
 * 边界收在这里：磁盘 IO、与上次写盘内容的去重、以及编辑停止后的防抖，
 * 其它模块只通过 [snapshot] / [load] / [autoSave] 接触设置。
 */
class SettingsController(
    private val logs: LogConsoleState,
    private val translation: TranslationController,
) {
    /** Last snapshot successfully written to disk; guards no-op auto-saves. */
    private var lastSaved: ApiSettings? = null

    /** 当前界面状态对应的待保存快照。 */
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

    /** 读取磁盘设置并应用到界面状态。 */
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
     * 自动保存：[snapshot] 停止变化 [AUTO_SAVE_DEBOUNCE] 后写盘。
     *
     * 在组合中启动；跳过首个快照，避免启动时无谓写盘。
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

    /** 写入 [settings]；与上次成功写盘的内容一致时跳过。 */
    private suspend fun save(settings: ApiSettings): Boolean {
        if (settings == lastSaved) return true
        val saved = withContext(Dispatchers.IO) { apiSetting.save(settings) }
        if (saved) lastSaved = settings
        return saved
    }
}
