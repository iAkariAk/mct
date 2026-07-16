package mct.gui.services

import com.github.ajalt.clikt.command.main
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mct.Env
import mct.cli.MCT
import java.io.File

private const val PROJECT_FILE = "mct.toml"

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
    require(name.isNotBlank()) { "请输入项目名称" }
    require(source.isNotBlank()) { "请选择源存档目录" }

    val workingDirectory = File(projectDirectory).absoluteFile
    require(workingDirectory.isDirectory) { "CLI 工作目录不存在: $workingDirectory" }

    runCliProjectCommand(
        workingDirectory = workingDirectory,
        arguments = listOf("project", "init", name, "--from", File(source).absolutePath),
    )

    val projectRoot = File(workingDirectory, name).absoluteFile
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
