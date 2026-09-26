@file:OptIn(ExperimentalSerializationApi::class)

package mct.gui.util

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.json.okio.encodeToBufferedSink
import mct.gui.platform.platformFileSystem
import mct.gui.platform.settingsDirectory
import okio.Path

val SettingsJson = Json { ignoreUnknownKeys = true; encodeDefaults = false }

interface Setting<T> {
    val path: Path
    fun exists(): Boolean = platformFileSystem.exists(path)

    /** Load the persisted value, or `null` when the file is absent or unreadable. */
    fun loadOrNull(): T?

    /** Load the persisted value, falling back to the default. */
    fun load(): T

    suspend fun save(value: T): Boolean
}

inline fun <reified T> setting(name: String, crossinline default: () -> T): Setting<T> = object : Setting<T> {
    override val path: Path = settingsDirectory / ("$name.json")

    /**
     * Serialises writes of this setting: the debounced auto-save, the flush on shutdown, and two
     * project-history updates would otherwise share the temp path and could move a half-written
     * file onto the target.
     *
     * A [Mutex] rather than a JVM lock because [save] suspends — it can resume on another thread,
     * and a thread-affine lock could not be released by that thread.
     */
    private val writeLock = Mutex()

    override fun loadOrNull(): T? {
        if (!platformFileSystem.exists(path)) return null
        return runCatching {
            platformFileSystem.read(path) { SettingsJson.decodeFromBufferedSource<T>(this) }
        }.getOrNull()
    }

    override fun load(): T = loadOrNull() ?: default()

    /**
     * Write the file atomically: a crash mid-write leaves the previous contents intact
     * instead of a truncated file that [loadOrNull] would silently replace with defaults.
     */
    override suspend fun save(value: T): Boolean = writeLock.withLock {
        runCatching {
            writeAtomically(platformFileSystem, path) { temp ->
                platformFileSystem.write(temp) { SettingsJson.encodeToBufferedSink<T>(value, this) }
            }
            true
        }.getOrElse { false }
    }
}
