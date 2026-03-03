package mct

import arrow.core.raise.Raise
import arrow.core.raise.either
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import okio.FileSystem
import kotlin.system.exitProcess

abstract class BaseCommand(
    name: String? = null
) : SuspendingCliktCommand(name) {
    val loggerLevels: List<String> by option("logger_level", "ll").multiple()

    val env: Env by lazy {
        try {
            val loggerLevels = loggerLevels.map { LoggerLevel.valueOf(it) }
            Env(
                FileSystem.SYSTEM,
                ConsoleLogger(this, loggerLevels)
            )
        } catch (_: Exception) {
            echo("Only ${LoggerLevel.entries} supported")
            exitProcess(1)
        }
    }

    override suspend fun run() {
        either {
            App()
        }.onLeft { error ->
            echo(error.message, err = true)
        }
    }

    context(_: Raise<MCTError>)
    protected abstract suspend fun App()
}