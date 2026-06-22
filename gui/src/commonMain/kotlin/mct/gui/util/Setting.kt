@file:OptIn(ExperimentalSerializationApi::class)

package mct.gui.util

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.json.okio.encodeToBufferedSink
import mct.util.SystemFileSystem
import okio.Path
import okio.Path.Companion.toPath

val SettingsJson = Json { ignoreUnknownKeys = true; encodeDefaults = false }
val settingsDir: Path = "${System.getProperty("user.home")}/.mct/".toPath()

interface Setting<T> {
    val path: Path
    fun load(): T
    fun save(value: T): Boolean
}

inline fun <reified T> setting(name: String, crossinline default: () -> T): Setting<T> = object : Setting<T> {
    override val path: Path = settingsDir / ("$name.json")
    override fun load(): T = try {
        if (SystemFileSystem.exists(path)) {
            SystemFileSystem.read(path) {
                SettingsJson.decodeFromBufferedSource<T>(this)
            }
        } else default()
    } catch (e: Exception) {
        println("[MCT] 加载设置失败 (${path.name}): ${e.message}")
        default()
    }

    override fun save(value: T) = try {
        SystemFileSystem.createDirectories(path.parent!!)
        SystemFileSystem.write(path) {
            SettingsJson.encodeToBufferedSink<T>(value, this)
        }
        true
    } catch (e: Exception) {
        println("[MCT] 保存设置失败 (${path.name}): ${e.message}")
        false
    }

}
