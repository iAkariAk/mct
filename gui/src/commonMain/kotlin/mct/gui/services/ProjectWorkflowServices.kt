package mct.gui.services

import com.github.ajalt.clikt.command.main
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mct.Env
import mct.cli.MCT
import java.io.File

private const val PROJECT_FILE = "mct.toml"

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
        arguments = listOf("project", "init", projectName, "--from", sourceRoot.absolutePath),
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
    require(projectDirectory.isNotBlank()) { "请选择项目目录" }
    val root = File(projectDirectory).absoluteFile
    require(File(root, PROJECT_FILE).isFile) { "未找到 $PROJECT_FILE: $root" }
    runCliProjectCommand(root, listOf("project", command))
}

context(env: Env)
private suspend fun runCliProjectCommand(
    workingDirectory: File,
    arguments: List<String>,
) {
    require(workingDirectory.isDirectory) { "CLI 工作目录不存在: $workingDirectory" }
    val cliArguments = arguments + listOf("--project-dir", workingDirectory.absolutePath)
    env.logger.info { "CLI > mct ${cliArguments.joinToString(" ")}" }
    withContext(Dispatchers.IO) {
        MCT().main(cliArguments.toTypedArray())
    }
}
