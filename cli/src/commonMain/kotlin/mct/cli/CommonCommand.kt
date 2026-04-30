package mct.cli

import arrow.core.getOrElse
import arrow.core.raise.Raise
import arrow.core.raise.either
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.groups.default
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.choice
import mct.Env
import mct.LoggerLevel
import mct.MCTError
import mct.MCTWorkspace
import mct.util.system.SystemFileSystem
import okio.FileSystem
import okio.Path.Companion.toPath

interface EnvProvider {
    val env: Env
}

val EnvProvider.fs get() = env.fs
val EnvProvider.logger get() = env.logger

abstract class BaseCommand(
    val name: String? = null,
    private val help: String? = null,
) : SuspendingCliktCommand(name), EnvProvider {
    override fun help(context: Context): String = help ?: super.help(context)

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

    override val env by lazy {
        Env(
            SystemFileSystem,
            ColorTerminalLogger(loggerLevels)
        )
    }

    val cacheDir by option("--cache-dir", help = "Path to cache directory").path().default(".".toPath())

    override suspend fun run() {
        either {
            context(fs) {
                App()
            }
        }.onLeft { error ->
            throw CliktError(error.message)
        }
    }

    context(_: Raise<MCTError>, fs: FileSystem)
    protected open suspend fun App() = Unit
}

abstract class WorkspaceCommand(
    name: String? = null,
    help: String? = null,
) : BaseCommand(name, help) {
    val input by option("--input", "-i", help = "The path to your map where there should be level.dat").path()
        .required()

    val workspace by lazy {
        either {
            MCTWorkspace(input, env)
        }.getOrElse {
            throw CliktError(it.message)
        }
    }
}

