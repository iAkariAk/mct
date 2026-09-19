package mct.gui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mct.gui.model.ProjectTextEntry
import mct.gui.model.ProjectTextFile

/**
 * The editable contents of one project text table, i.e. `mappings.json` or `terms.json`.
 *
 * The list is the working copy: the page adds, changes and removes entries in memory and only
 * [save] touches the file, the same way the configuration editor works. File order is preserved,
 * because that is the order the CLI writes and a user scanning the file expects to see.
 */
class ProjectTableEditor(val file: ProjectTextFile) {
    var entries by mutableStateOf<List<ProjectTextEntry>>(emptyList())
        private set

    var isDirty by mutableStateOf(false)
        private set

    var isSaving by mutableStateOf(false)
        private set

    /**
     * The configured path the current rows were read from, or `null` while nothing is loaded.
     *
     * The controller compares it with the live configuration before saving: the rows and the path
     * they are written to must belong to the same file.
     */
    var loadedPath by mutableStateOf<String?>(null)
        private set

    /** Serialises saves so two of them cannot write the same file at once. */
    private val saveLock = Mutex()

    /** Adopt the table read from disk. */
    fun applyLoaded(loaded: List<ProjectTextEntry>, path: String? = null) {
        entries = loaded
        loadedPath = path
        isDirty = false
    }

    /**
     * Add an entry. The source text is the key of the table, so a duplicate is rejected rather than
     * silently overwriting a translation.
     */
    fun add(source: String, target: String?) {
        val key = normalizeSource(source)
        require(entries.none { it.source == key }) { "「$key」已存在" }
        entries = entries + ProjectTextEntry(key, target.orNull())
        isDirty = true
    }

    /**
     * Change one entry. [previousSource] is its key before the edit; changing the key is allowed,
     * except onto another entry that already uses it.
     */
    fun update(previousSource: String, source: String, target: String?) {
        val key = normalizeSource(source)
        require(entries.none { it.source == key && it.source != previousSource }) { "「$key」已存在" }
        val index = entries.indexOfFirst { it.source == previousSource }
        val updated = ProjectTextEntry(key, target.orNull())
        entries = if (index < 0) entries + updated else entries.toMutableList().also { it[index] = updated }
        isDirty = true
    }

    fun remove(source: String) {
        entries = entries.filterNot { it.source == source }
        isDirty = true
    }

    /**
     * Write the table through [write] (supplied by the controller, which knows the file and path).
     *
     * The payload is snapshotted once the lock is taken, so a save queued behind another writes the
     * newest rows instead of a stale copy. [isDirty] is only cleared when the rows are still the
     * ones that were written: an edit made while the write was in flight is not in the file, and
     * declaring it saved would hide it until the next reload.
     */
    suspend fun save(write: suspend (List<ProjectTextEntry>) -> Unit): Result<Unit> = saveLock.withLock {
        val snapshot = entries
        isSaving = true
        runCatching { write(snapshot) }
            .onSuccess { if (entries == snapshot) isDirty = false }
            .also { isSaving = false }
    }

    private fun normalizeSource(source: String): String {
        val trimmed = source.trim()
        require(trimmed.isNotEmpty()) { "原文不能为空" }
        return trimmed
    }
}

/**
 * The terms table always stores a translation, so an empty field there is an error rather than a
 * "not translated yet" value; mapping entries use `null` for exactly that state.
 */
private fun String?.orNull(): String? = this?.takeIf { it.isNotEmpty() }
