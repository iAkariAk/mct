package mct.cli

import arrow.continuations.SuspendApp
import arrow.core.getOrElse
import arrow.core.raise.Raise
import arrow.core.raise.either
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.groups.default
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.mordant.rendering.TextColors
import kotlinx.coroutines.CancellationException
import mct.*
import mct.util.SystemFileSystem
import okio.Path.Companion.toPath

private class MCTException(val error: MCTError) : Exception(error.message)

abstract class BaseCommand(
    val name: String? = null,
    private val help: String? = null,
) : SuspendingCliktCommand(name), EnvHolder {
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
            ColorTerminalLogger(loggerLevels),
            notifier = CliNotifier
        )
    }

    val cacheDir by option("--cache-dir", help = "Path to cache directory").path().default(".".toPath())

    override suspend fun run(): Unit = try {
        SuspendApp {
            either {
                App()
            }.getOrElse {
                terminal.println(TextColors.red(it.message))
            }
        }
    } catch (_: CancellationException) {
    } catch (e: PrintMessage) {
        terminal.println(TextColors.red(e.message ?: ""))
    }

    context(_: Raise<MCTError>)
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

fun BaseCommand.printlnGreen(message: Any?) = terminal.println(TextColors.green(message.toString()))
fun BaseCommand.printlnYellow(message: Any?) = terminal.println(TextColors.yellow(message.toString()))
fun BaseCommand.printlnBlue(message: Any?) = terminal.println(TextColors.blue(message.toString()))
fun BaseCommand.printlnRed(message: Any?) = terminal.println(TextColors.red(message.toString()))