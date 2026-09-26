package mct.gui.services

import kotlinx.coroutines.withContext
import mct.Env
import mct.extra.ai.translator.TermTable
import mct.gui.model.ProjectTextEntry
import mct.gui.platform.ioDispatcher
import mct.gui.util.isRegularFile
import mct.gui.util.joinPath
import mct.gui.util.resolveAgainstWorkingDirectory
import mct.gui.util.writeAtomically
import mct.kit.TranslationMapping
import mct.serializer.MCTJson
import mct.serializer.PrettyJson
import okio.Path.Companion.toPath

/** File that identifies a project root; the CLI (`ProjectCommands`) requires it in the project dir. */
const val PROJECT_FILE = "mct.toml"

/** Pool of texts that still have no mapping, written by `mct project update` (`ProjectCommands.MISSING`). */
const val PROJECT_MISSING_FILE = "missing.json"

/**
 * Validate a project name before it becomes a directory under the working directory.
 *
 * Returns an error message, or `null` when the name is usable. Rejecting `.`/`..` and path
 * separators is what keeps the CLI from resolving the project directory outside the working
 * directory (and from deleting or overwriting files there).
 */
fun projectNameError(name: String): String? {
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() -> "请输入项目名称"
        trimmed == "." || trimmed == ".." -> "项目名称不能是 . 或 .."
        trimmed.any { it == '/' || it == '\\' } -> "项目名称不能包含路径分隔符"
        trimmed.endsWith(":") -> "项目名称不能以冒号结尾"
        // The name is a positional argument, so a leading dash makes Clikt read it as options and
        // the whole run fails with a usage error instead of creating anything.
        trimmed.startsWith("-") -> "项目名称不能以 - 开头"
        trimmed != name -> "项目名称首尾不能有空白字符"
        else -> null
    }
}

/**
 * The project root behind [projectDirectory], which is the directory holding [PROJECT_FILE].
 *
 * `project init` is given the *parent* directory and creates the project inside it, so both the
 * parent and the root itself are accepted; anything else is rejected here instead of surfacing as
 * a confusing failure later.
 */
fun requireProjectRoot(projectDirectory: String): String {
    require(projectDirectory.isNotBlank()) { "请选择项目目录" }
    val root = resolveAgainstWorkingDirectory(projectDirectory)
    require(isRegularFile(joinPath(root, PROJECT_FILE))) { "未找到 $PROJECT_FILE: $root" }
    return root
}

/** Resolve a path stored in `mct.toml`: relative paths hang off the project root, like the CLI. */
fun resolveProjectPath(projectDirectory: String, path: String): String =
    if (path.toPath().isAbsolute) path else joinPath(projectDirectory, path)

/**
 * The mapping file (`config.mappings`) as display entries, in file order.
 *
 * A missing file is an empty mapping rather than an error: a project that has not been translated
 * yet legitimately has none.
 */
context(env: Env)
suspend fun readProjectMappings(projectDirectory: String, mappingsPath: String): List<ProjectTextEntry> =
    withContext(ioDispatcher) {
        val path = resolveProjectPath(projectDirectory, mappingsPath)
        if (!isRegularFile(path)) return@withContext emptyList()
        val mapping = MCTJson.decodeFromString<TranslationMapping>(env.fs.read(path.toPath()) { readUtf8() })
        env.logger.info { "已加载 ${mapping.size} 条映射: $path" }
        mapping.entries.map { ProjectTextEntry(it.key, it.value) }
    }

/** Unmapped texts (`missing.json`) as entries without a target, in file order. */
context(env: Env)
suspend fun readProjectMissing(projectDirectory: String): List<ProjectTextEntry> =
    withContext(ioDispatcher) {
        val path = joinPath(requireProjectRoot(projectDirectory), PROJECT_MISSING_FILE)
        if (!isRegularFile(path)) return@withContext emptyList()
        // The CLI's pool is a `Set` (`TranslationPool`), so duplicates are not a thing it can write;
        // decoding as a `Set` keeps a hand-edited file with repeated texts from producing two rows
        // with the same list key.
        val pool = MCTJson.decodeFromString<Set<String>>(env.fs.read(path.toPath()) { readUtf8() })
        env.logger.info { "已加载 ${pool.size} 条未映射文本: $path" }
        pool.map { ProjectTextEntry(it, null) }
    }

/** The term table (`config.terms`) as display entries, in file order. */
context(env: Env)
suspend fun readProjectTerms(projectDirectory: String, termsPath: String): List<ProjectTextEntry> =
    withContext(ioDispatcher) {
        val path = resolveProjectPath(projectDirectory, termsPath)
        if (!isRegularFile(path)) return@withContext emptyList()
        val terms = MCTJson.decodeFromString<TermTable>(env.fs.read(path.toPath()) { readUtf8() })
        env.logger.info { "已加载 ${terms.size} 条术语: $path" }
        terms.entries.map { ProjectTextEntry(it.key, it.value) }
    }

/**
 * Write the mapping table back to `config.mappings`.
 *
 * Shape and pretty-printing are the CLI's own (`TranslationMapping`, `pretty_json`), so a file the
 * GUI writes is what `mct project translate` would have written for the same table.
 */
context(env: Env)
suspend fun writeProjectMappings(
    projectDirectory: String,
    mappingsPath: String,
    entries: List<ProjectTextEntry>,
    pretty: Boolean,
) = withContext(ioDispatcher) {
    val mapping: TranslationMapping = LinkedHashMap<String, String?>(entries.size).apply {
        entries.forEach { entry -> put(entry.source, entry.target) }
    }
    writeProjectJson(projectDirectory, mappingsPath, encodeTable(mapping, pretty))
}

/** Write the term table back to `config.terms`; a term without a translation is dropped. */
context(env: Env)
suspend fun writeProjectTerms(
    projectDirectory: String,
    termsPath: String,
    entries: List<ProjectTextEntry>,
    pretty: Boolean,
) = withContext(ioDispatcher) {
    val terms: TermTable = LinkedHashMap<String, String>(entries.size).apply {
        entries.forEach { entry -> entry.target?.let { target -> put(entry.source, target) } }
    }
    writeProjectJson(projectDirectory, termsPath, encodeTable(terms, pretty))
}

/** Encode with the same format the CLI uses for the file, honouring `pretty_json`. */
private inline fun <reified T> encodeTable(value: T, pretty: Boolean): String =
    if (pretty) PrettyJson.encodeToString(value) else MCTJson.encodeToString(value)

context(env: Env)
private fun writeProjectJson(projectDirectory: String, path: String, text: String) {
    writeAtomically(env.fs, resolveProjectPath(projectDirectory, path).toPath()) { temp ->
        env.fs.write(temp) { writeUtf8(text) }
    }
}
