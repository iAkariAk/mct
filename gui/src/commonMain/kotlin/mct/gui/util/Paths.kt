package mct.gui.util

import mct.gui.platform.appWorkingDirectory
import mct.gui.platform.platformFileSystem
import okio.Path
import okio.Path.Companion.toPath

/**
 * Path helpers over [platformFileSystem].
 *
 * They exist because the GUI used `java.io.File` for all of this, which is JVM-only. okio answers
 * the same questions from common code and, on both platforms, asks the same underlying filesystem
 * the CLI will later open the path with — so a path accepted here is the path the run uses.
 */

/** Whether [path] exists, as a file or a directory. Unreadable parents read as "absent". */
fun pathExists(path: String): Boolean =
    runCatching { platformFileSystem.metadataOrNull(path.toPath()) != null }.getOrDefault(false)

/** Whether [path] exists and is a regular file. */
fun isRegularFile(path: String): Boolean =
    runCatching { platformFileSystem.metadataOrNull(path.toPath())?.isRegularFile == true }.getOrDefault(false)

/** Whether [path] exists and is a directory. */
fun isDirectory(path: String): Boolean =
    runCatching { platformFileSystem.metadataOrNull(path.toPath())?.isDirectory == true }.getOrDefault(false)

/** [path] as an absolute path against the process working directory; unchanged when it already is. */
fun absolutePathOf(path: String): String =
    if (path.toPath().isAbsolute) path else resolveAgainstWorkingDirectory(path)

/** Join [base] and [child] with the platform separator; [child] may itself be a relative path. */
fun joinPath(base: String, child: String): String = (base.toPath() / child).toString()

/** The `*.json` files directly under [dir], as absolute paths sorted by name; empty when absent. */
fun listJsonFiles(dir: String): List<String> = runCatching {
    platformFileSystem.list(dir.toPath())
        .filter { platformFileSystem.metadataOrNull(it)?.isRegularFile == true && it.name.endsWith(".json") }
        .map { it.toString() }
        .sorted()
}.getOrDefault(emptyList())

/** The last segment of [path]. */
fun fileNameOf(path: String): String = runCatching { path.toPath().name }.getOrDefault(path)

/** Resolve [path] against [appWorkingDirectory] when it is relative. */
fun resolveAgainstWorkingDirectory(path: String): String {
    val resolved = path.toPath()
    return if (resolved.isAbsolute) resolved.toString() else joinPath(appWorkingDirectory, path)
}

/**
 * [path] itself, or the nearest existing ancestor of it; `null` when nothing on the way up exists.
 *
 * Used to tell "this project's directory is gone" from "this project's directory moved", which
 * decides whether the history entry is dropped or kept.
 */
fun existingAncestorOrNull(path: String): String? {
    var current: Path? = path.toPath()
    while (current != null) {
        if (pathExists(current.toString())) return current.toString()
        current = current.parent
    }
    return null
}
