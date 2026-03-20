package mct.cli

import arrow.core.raise.Raise
import arrow.core.raise.either
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import mct.Env
import mct.LoggerLevel
import mct.MCTError
import mct.MCTWorkspace
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.system.exitProcess

abstract class BaseCommand(
    name: String? = null
) : SuspendingCliktCommand(name) {
    val loggerLevels: List<String> by option("--logger-level", "-l").multiple(listOf("Info", "Warning", "Debug", "Error"))

    val env: Env by lazy {
        try {
            val loggerLevels = loggerLevels.map { LoggerLevel.valueOf(it) }
            Env(
                FileSystem.SYSTEM,
                ColorTerminalLogger(loggerLevels)
            )
        } catch (_: Exception) {
            echo("Only ${LoggerLevel.entries} supported")
            exitProcess(1)
        }
    }

    override suspend fun run() {
        either {
            context(env.fs) {
                App()
            }
        }.onLeft { error ->
            echo(error.message, err = true)
        }
    }

    context(_: Raise<MCTError>, fs: FileSystem)
    protected abstract suspend fun App()
}

abstract class WorkspaceCommand(
    name: String? = null
) : BaseCommand(name) {
    val input: String by option("--input", "-i").required()

    val workspace by lazy { MCTWorkspace(input.toPath(), env) }
}

