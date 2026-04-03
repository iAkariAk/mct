package mct.cli

import arrow.core.raise.Raise
import arrow.core.raise.either
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.parameters.groups.default
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.choice
import mct.Env
import mct.LoggerLevel
import mct.MCTError
import mct.MCTWorkspace
import okio.FileSystem

abstract class BaseCommand(
    name: String? = null
) : SuspendingCliktCommand(name) {
    val loggerLevels by mutuallyExclusiveOptions(
        option("--logger-level", "-l").choice(
            "Info",
            "Warning",
            "Debug",
            "Error"
        ).convert {
            LoggerLevel.valueOf(it)
        }.multiple(),
        option("--verbose", "-V").flag().convert { f -> LoggerLevel.Verbose.takeIf { f } }
    ).default(emptyList())

    val env: Env by lazy {
        Env(
            FileSystem.SYSTEM,
            ColorTerminalLogger(loggerLevels)
        )
    }

    override suspend fun run() {
        either {
            context(env.fs) {
                App()
            }
        }.onLeft { error ->
            throw CliktError(error.message)
        }
    }

    context(_: Raise<MCTError>, fs: FileSystem)
    protected abstract suspend fun App()
}

abstract class WorkspaceCommand(
    name: String? = null
) : BaseCommand(name) {
    val input by option("--input", "-i").path().required()

    val workspace by lazy { MCTWorkspace(input, env) }
}

