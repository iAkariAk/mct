package mct.gui.services

import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import mct.Env
import mct.LoggerLevel
import mct.cli.MCT
import mct.cli.cmd.project.MCTToml
import mct.cli.cmd.project.ProjectConfig
import mct.extra.ai.translator.TermTable
import mct.gui.model.ProjectTextEntry
import mct.gui.model.ProjectTranslationEngine
import mct.kit.TranslationMapping
import mct.serializer.MCTJson
import mct.serializer.PrettyJson
import java.io.File
import java.io.OutputStream
import java.io.PrintStream

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
        trimmed != name -> "项目名称首尾不能有空白字符"
        else -> null
    }
}

/**
 * Create a project by delegating to the CLI implementation.
 *
 * [projectDirectory] is the CLI working directory. The CLI creates
 * `[projectDirectory]/[name]`, copies the source world into `src`, and writes
 * `mct.toml`. Returning the new root lets the GUI immediately switch from the
 * parent directory to the created project.
 */
context(env: Env)
suspend fun initialiseProject(
    projectDirectory: String,
    name: String,
    source: String,
    engine: ProjectTranslationEngine,
): String {
    require(projectDirectory.isNotBlank()) { "请选择 CLI 工作目录" }
    val nameError = projectNameError(name)
    require(nameError == null) { nameError.orEmpty() }
    require(source.isNotBlank()) { "请选择源存档目录" }

    val workingDirectory = File(projectDirectory).absoluteFile
    require(workingDirectory.isDirectory) { "CLI 工作目录不存在: $workingDirectory" }

    val projectName = name.trim()
    val projectRoot = File(workingDirectory, projectName).canonicalFile
    require(projectRoot.parentFile == workingDirectory.canonicalFile) {
        "项目名称解析后不在工作目录内: $projectRoot"
    }

    // The CLI creates `projectRoot/src` before walking the source tree; if the source contains
    // that directory, the copy recurses into itself.
    val sourceRoot = File(source).canonicalFile
    val sourcePrefix = sourceRoot.path + File.separator
    val projectPrefix = projectRoot.path + File.separator
    require(!(projectRoot.path + File.separator + "src").startsWith(sourcePrefix)) {
        "源存档不能是项目目录或其上级目录（会产生自我拷贝）"
    }
    require(!sourceRoot.path.startsWith(projectPrefix)) {
        "源存档不能位于项目目录内"
    }

    runCliProjectCommand(
        workingDirectory = workingDirectory,
        arguments = listOf(
            "project", "init", projectName,
            "--from", sourceRoot.absolutePath,
            "--translation-engine", engine.key,
        ),
    )

    require(File(projectRoot, PROJECT_FILE).isFile) {
        "CLI 未创建 $PROJECT_FILE，请检查上方输出"
    }
    return projectRoot.path
}

context(env: Env)
suspend fun updateProject(projectDirectory: String) =
    runProjectCommand(projectDirectory, "update")

context(env: Env)
suspend fun extractProjectTerms(projectDirectory: String) =
    runProjectCommand(projectDirectory, "term")

context(env: Env)
suspend fun translateProject(projectDirectory: String) =
    runProjectCommand(projectDirectory, "translate")

context(env: Env)
suspend fun buildProject(projectDirectory: String) =
    runProjectCommand(projectDirectory, "build")

context(env: Env)
suspend fun assembleProjectPatch(projectDirectory: String) =
    runProjectCommand(projectDirectory, "patch")

context(env: Env)
private suspend fun runProjectCommand(projectDirectory: String, command: String) {
    runCliProjectCommand(requireProjectRoot(projectDirectory), listOf("project", command))
}

context(env: Env)
private suspend fun runCliProjectCommand(
    workingDirectory: File,
    arguments: List<String>,
) {
    require(workingDirectory.isDirectory) { "CLI 工作目录不存在: $workingDirectory" }
    val cliArguments = arguments + listOf(
        "--project-dir", workingDirectory.absolutePath,
        // The CLI is silent by default (`ColorTerminalLogger(emptyList())` drops everything); its
        // info and warning lines are the progress detail this console exists for.
        "-l", "Info", "-l", "Warning", "-l", "Error",
    )
    env.logger.info { "CLI > mct ${arguments.joinToString(" ")}" }
    withContext(Dispatchers.IO) {
        // The CLI prints through its own terminal, which writes to `System.out`; in process that is
        // the GUI's stdout, so everything the command reports (progress, counts, errors) would be
        // invisible. Its output is routed into the GUI console for the duration of the run.
        //
        // `System.out` is global, so the swap is serialized: a cancelled command's teardown must not
        // run between a successor's swap in and its own, which would either route the second run's
        // output nowhere or leave stdout bound to a dead capture stream.
        cliRunLock.withLock {
            val originalOut = System.out
            val originalErr = System.err
            val console = PrintStream(LineCollectingStream { line -> logCliLine(env, line) }, true)
            System.setOut(console)
            System.setErr(console)
            try {
                // ANSI is forced on: a colour is the only signal that a line the CLI printed
                // through `Terminal.println` is an error or a warning, and the capture turns it
                // into a level.
                MCT()
                    .apply { configureContext { terminal = Terminal(ansiLevel = AnsiLevel.ANSI16) } }
                    .main(cliArguments.toTypedArray())
                // The CLI's own logger prints from its own single-thread dispatcher, so its last
                // lines can arrive after the command returns; without this pause they would go to
                // the real stdout and never reach the console.
                delay(150)
            } finally {
                System.out.flush()
                System.err.flush()
                System.setOut(originalOut)
                System.setErr(originalErr)
            }
        }
    }
}

/**
 * Serializes the `System.out` swap around a CLI run.
 *
 * A [Mutex] rather than a JVM lock on purpose: the run suspends, so it can resume on another
 * thread, and a thread-affine lock could not be released by that thread.
 */
private val cliRunLock = Mutex()

/** SGR escape sequences; stripped for display, read to recover the level. */
private val AnsiEscape = Regex("\u001B\\[[0-9;]*[A-Za-z]")

/** The level prefixes the CLI's `ColorTerminalLogger` writes (`LoggerLevel.prefix`). */
private val LevelPrefixes = listOf(
    "[INFO]" to LoggerLevel.Info,
    "[DEBUG]" to LoggerLevel.Debug,
    "[WARN]" to LoggerLevel.Warning,
    "[ERROR]" to LoggerLevel.Error,
)

/**
 * Route one captured CLI line to the console at the level it reports.
 *
 * The CLI states a level in one of two ways: its logger prefixes `[INFO]`/`[WARN]`/…, and its plain
 * `Terminal.println` calls colour the whole line (red for an error, yellow for a warning, green and
 * blue for progress). The prefix wins when both are present; anything else is informational.
 *
 * The prefix itself is stripped: the console renders the level as its own badge, so keeping the text
 * would print it twice.
 */
private fun logCliLine(env: Env, raw: String) {
    val text = raw.replace(AnsiEscape, "").trimEnd()
    if (text.isBlank()) return
    val prefixed = LevelPrefixes.firstOrNull { text.startsWith(it.first) }
    val level = prefixed?.second ?: levelFromAnsi(raw) ?: LoggerLevel.Info
    val message = prefixed?.let { text.removePrefix(it.first).trimStart() } ?: text
    env.logger.log(level, message)
}

/**
 * The level behind the colours in [raw], most severe first so a line that highlights a number in
 * red still reads as an error.
 */
private fun levelFromAnsi(raw: String): LoggerLevel? {
    val codes = AnsiEscape.findAll(raw)
        .flatMap { match ->
            match.value.removePrefix("\u001B[").removeSuffix("m")
                .split(';')
                .mapNotNull(String::toIntOrNull)
        }
        .toList()
    return when {
        codes.any { it == 31 || it == 91 } -> LoggerLevel.Error
        codes.any { it == 33 || it == 93 } -> LoggerLevel.Warning
        codes.any { it == 90 } -> LoggerLevel.Debug
        codes.any { it in setOf(32, 34, 35, 36, 92, 94, 95, 96) } -> LoggerLevel.Info
        else -> null
    }
}

/**
 * An [OutputStream] that hands over complete lines. Mordant renders one line per write, but nothing
 * guarantees that, so the tail of a partial line is kept until its newline arrives.
 */
private class LineCollectingStream(private val onLine: (String) -> Unit) : OutputStream() {
    private val buffer = StringBuilder()

    // The CLI writes from its own threads (its logger has a dispatcher of its own), so writes are
    // serialized to keep lines whole.
    @Synchronized
    override fun write(b: Int) {
        val char = b.toChar()
        if (char == '\n') {
            onLine(buffer.toString())
            buffer.clear()
        } else if (char != '\r') {
            buffer.append(char)
        }
    }

    @Synchronized
    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        for (i in offset until offset + length) write(bytes[i].toInt())
    }
}

/**
 * The project root behind [projectDirectory], which is the directory holding [PROJECT_FILE].
 *
 * `project init` is given the *parent* directory and creates the project inside it, so both the
 * parent and the root itself are accepted; anything else is rejected here instead of surfacing as
 * a confusing failure later.
 */
fun requireProjectRoot(projectDirectory: String): File {
    require(projectDirectory.isNotBlank()) { "请选择项目目录" }
    val root = File(projectDirectory).absoluteFile
    require(File(root, PROJECT_FILE).isFile) { "未找到 $PROJECT_FILE: $root" }
    return root
}

/** Resolve a path stored in `mct.toml`: relative paths hang off the project root, like the CLI. */
fun resolveProjectPath(projectDirectory: String, path: String): File {
    val file = File(path)
    return if (file.isAbsolute) file else File(projectDirectory, path)
}

/**
 * Read `mct.toml` into the CLI's own [ProjectConfig].
 *
 * Decoding uses the CLI's [MCTToml], so exactly the files the CLI accepts are accepted here and
 * the GUI writes back the same shape the CLI's own writer produces. This is a read/write of the
 * *CLI's* configuration type, not a second parser: nothing about `mct.toml` is re-implemented in
 * the GUI.
 */
context(env: Env)
suspend fun readProjectConfig(projectDirectory: String): ProjectConfig = withContext(Dispatchers.IO) {
    val file = File(requireProjectRoot(projectDirectory), PROJECT_FILE)
    env.logger.info { "读取项目配置: ${file.path}" }
    MCTToml.decodeFromString<ProjectConfig>(file.readText())
}

/**
 * Write [config] back to `mct.toml`, comments included (the CLI's `@TomlComments` survive).
 *
 * `ai.literature_style` is a `@TomlMultiline` block, and ktoml's encoder emits one newline more
 * than its decoder consumes. Writing the value as-is would therefore append a newline to that
 * prompt on every save, so the surplus one is dropped here: a save is then idempotent, and the CLI
 * still reads back exactly the value the GUI shows.
 *
 * The encoded text is decoded once before it is written. ktoml's writer does not escape every
 * backslash, so a value can encode into something its own reader rejects; the CLI would then fail
 * to read the project at all. Refusing the write keeps the previous file intact instead.
 */
context(env: Env)
suspend fun writeProjectConfig(projectDirectory: String, config: ProjectConfig) = withContext(Dispatchers.IO) {
    val file = File(requireProjectRoot(projectDirectory), PROJECT_FILE)
    val encoded = config.copy(
        ai = config.ai.copy(literatureStyle = config.ai.literatureStyle.removeSuffix("\n")),
    )
    val text = MCTToml.encodeToString(encoded)
    runCatching { MCTToml.decodeFromString<ProjectConfig>(text) }.onFailure { cause ->
        throw IllegalArgumentException(
            "$PROJECT_FILE 的取值无法被 CLI 读回（${cause.message}），已取消保存；" +
                "请检查是否含有反斜杠转义序列（如 \\u、\\U）",
            cause,
        )
    }
    file.writeText(text)
    env.logger.info { "已写入项目配置: ${file.path}" }
}

/**
 * The mapping file (`config.mappings`) as display entries, in file order.
 *
 * A missing file is an empty mapping rather than an error: a project that has not been translated
 * yet legitimately has none.
 */
context(env: Env)
suspend fun readProjectMappings(projectDirectory: String, mappingsPath: String): List<ProjectTextEntry> =
    withContext(Dispatchers.IO) {
        val file = resolveProjectPath(projectDirectory, mappingsPath)
        if (!file.isFile) return@withContext emptyList()
        val mapping = MCTJson.decodeFromString<TranslationMapping>(file.readText())
        env.logger.info { "已加载 ${mapping.size} 条映射: ${file.path}" }
        mapping.entries.map { ProjectTextEntry(it.key, it.value) }
    }

/** Unmapped texts (`missing.json`) as entries without a target, in file order. */
context(env: Env)
suspend fun readProjectMissing(projectDirectory: String): List<ProjectTextEntry> =
    withContext(Dispatchers.IO) {
        val file = File(requireProjectRoot(projectDirectory), PROJECT_MISSING_FILE)
        if (!file.isFile) return@withContext emptyList()
        val pool = MCTJson.decodeFromString<List<String>>(file.readText())
        env.logger.info { "已加载 ${pool.size} 条未映射文本: ${file.path}" }
        pool.map { ProjectTextEntry(it, null) }
    }

/** The term table (`config.terms`) as display entries, in file order. */
context(env: Env)
suspend fun readProjectTerms(projectDirectory: String, termsPath: String): List<ProjectTextEntry> =
    withContext(Dispatchers.IO) {
        val file = resolveProjectPath(projectDirectory, termsPath)
        if (!file.isFile) return@withContext emptyList()
        val terms = MCTJson.decodeFromString<TermTable>(file.readText())
        env.logger.info { "已加载 ${terms.size} 条术语: ${file.path}" }
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
) = withContext(Dispatchers.IO) {
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
) = withContext(Dispatchers.IO) {
    val terms: TermTable = LinkedHashMap<String, String>(entries.size).apply {
        entries.forEach { entry -> entry.target?.let { target -> put(entry.source, target) } }
    }
    writeProjectJson(projectDirectory, termsPath, encodeTable(terms, pretty))
}

/** Encode with the same format the CLI uses for the file, honouring `pretty_json`. */
private inline fun <reified T> encodeTable(value: T, pretty: Boolean): String =
    if (pretty) PrettyJson.encodeToString(value) else MCTJson.encodeToString(value)

private fun writeProjectJson(projectDirectory: String, path: String, text: String) {
    val file = resolveProjectPath(projectDirectory, path)
    file.parentFile?.mkdirs()
    file.writeText(text)
}
