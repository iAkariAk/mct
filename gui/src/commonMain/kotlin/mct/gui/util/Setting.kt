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
    fun exists(): Boolean = SystemFileSystem.exists(path)

    /** Load the persisted value, or `null` when the file is absent or unreadable. */
    fun loadOrNull(): T?

    /** Load the persisted value, falling back to the default. */
    fun load(): T

    fun save(value: T): Boolean
}

inline fun <reified T> setting(name: String, crossinline default: () -> T): Setting<T> = object : Setting<T> {
    override val path: Path = settingsDir / ("$name.json")

    override fun loadOrNull(): T? {
        if (!SystemFileSystem.exists(path)) return null
        return runCatching {
            SystemFileSystem.read(path) { SettingsJson.decodeFromBufferedSource<T>(this) }
        }.getOrNull()
    }

    override fun load(): T = loadOrNull() ?: default()

    /**
     * Write the file atomically: a crash mid-write leaves the previous contents intact
     * instead of a truncated file that [loadOrNull] would silently replace with defaults.
     *
     * Writes are serialised per setting: two of them (the debounced auto-save and the flush on
     * window close, or two project-history updates) would otherwise share the temp path and could
     * move a half-written file onto the target.
     */
    override fun save(value: T): Boolean = synchronized(this) {
        runCatching {
            writeAtomically(SystemFileSystem, path) { temp ->
                SystemFileSystem.write(temp) { SettingsJson.encodeToBufferedSink<T>(value, this) }
            }
            true
        }.getOrElse { false }
    }
}
