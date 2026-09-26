package mct.gui.services

import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import mct.Env
import mct.cli.cmd.project.MCTToml
import mct.cli.cmd.project.ProjectConfig
import mct.gui.model.ProjectTranslationEngine
import mct.gui.platform.ioDispatcher
import mct.gui.util.*
import okio.Path.Companion.toPath

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

    val workingDirectory = absolutePathOf(projectDirectory).toPath().normalized()
    require(isDirectory(workingDirectory.toString())) { "CLI 工作目录不存在: $workingDirectory" }

    val projectName = name.trim()
    val projectRoot = (workingDirectory / projectName).normalized()
    require(projectRoot.parent == workingDirectory) {
        "项目名称解析后不在工作目录内: $projectRoot"
    }

    // The CLI creates `projectRoot/src` before walking the source tree; if the source contains
    // that directory, the copy recurses into itself.
    val sourceRoot = absolutePathOf(source).toPath().normalized()
    val sourcePrefix = "$sourceRoot${okio.Path.DIRECTORY_SEPARATOR}"
    val projectPrefix = "$projectRoot${okio.Path.DIRECTORY_SEPARATOR}"
    require(!"$projectRoot${okio.Path.DIRECTORY_SEPARATOR}src".startsWith(sourcePrefix)) {
        "源存档不能是项目目录或其上级目录（会产生自我拷贝）"
    }
    require(!sourceRoot.toString().startsWith(projectPrefix)) {
        "源存档不能位于项目目录内"
    }

    runCliCommand(
        listOf(
            "project", "init", projectName,
            // `project init` reads `--project-dir` as the directory it creates `[name]` under, so
            // the working directory is what the CLI must be told, not the project root it derives
            // from it. Spelled out here rather than appended by the runner: the console line below
            // is the only record of the directory a run used.
            "--project-dir", workingDirectory.toString(),
            "--from", sourceRoot.toString(),
            "--translation-engine", engine.key,
        ),
    )

    require(isRegularFile(joinPath(projectRoot.toString(), PROJECT_FILE))) {
        "CLI 未创建 $PROJECT_FILE，请检查上方输出"
    }
    return projectRoot.toString()
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
    val root = requireProjectRoot(projectDirectory)
    runCliCommand(listOf("project", command, "--project-dir", root))
}

/**
 * Run one `mct` command in process, with [arguments] as its complete argument list.
 *
 * The caller spells out every path the command needs, `--project-dir` included; nothing is appended
 * behind its back, so what the console reports as `CLI > mct …` is exactly what ran.
 *
 * An expect because the implementation has to swap the process's `System.out` to capture what the
 * CLI prints; both JVM targets do that identically, so the actual lives in `jvmSharedMain`.
 */
context(env: Env)
internal expect suspend fun runCliCommand(arguments: List<String>)

/**
 * Read `mct.toml` into the CLI's own [ProjectConfig].
 *
 * Decoding uses the CLI's [MCTToml], so exactly the files the CLI accepts are accepted here and
 * the GUI writes back the same shape the CLI's own writer produces. This is a read/write of the
 * *CLI's* configuration type, not a second parser: nothing about `mct.toml` is re-implemented in
 * the GUI.
 */
context(env: Env)
suspend fun readProjectConfig(projectDirectory: String): ProjectConfig = withContext(ioDispatcher) {
    val path = joinPath(requireProjectRoot(projectDirectory), PROJECT_FILE)
    env.logger.info { "读取项目配置: $path" }
    MCTToml.decodeFromString<ProjectConfig>(env.fs.read(path.toPath()) { readUtf8() })
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
suspend fun writeProjectConfig(projectDirectory: String, config: ProjectConfig) = withContext(ioDispatcher) {
    val path = joinPath(requireProjectRoot(projectDirectory), PROJECT_FILE)
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
    writeAtomically(env.fs, path.toPath()) { temp ->
        env.fs.write(temp) { writeUtf8(text) }
    }
    env.logger.info { "已写入项目配置: $path" }
}
