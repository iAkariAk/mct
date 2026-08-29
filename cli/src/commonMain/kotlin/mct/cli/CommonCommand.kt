package mct.cli

import arrow.core.getOrElse
import arrow.core.raise.Raise
import arrow.core.raise.either
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.groups.default
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.mordant.rendering.TextColors
import mct.*
import mct.command.BuiltinCommandDataPatterns
import mct.command.BuiltinCommandPatterns
import mct.command.BuiltinMinecraftComponentPatterns
import mct.dp.mcjson.BuiltinMCJsonPatterns
import mct.nbt.BuiltinNbtPatterns
import mct.util.SystemFileSystem
import mct.util.io.readJson
import okio.Path
import okio.Path.Companion.toPath

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

    override suspend fun run() = try {
        either {
            App()
        }.getOrElse {
            terminal.println(TextColors.red(it.message))
        }
    } catch (e: Panic) {
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

abstract class ExtractingCommand(
    name: String? = null,
    help: String? = null,
) : WorkspaceCommand(name, help) {
    private val mcjson by option("--pattern-mcjson-pattern").path()
    private val nbt by option("--pattern-nbt-pattern").path()
    private val command by option("--pattern-command").path()
    private val commandData by option("--pattern-command-data").path()
    private val commandComponent by option("--pattern-command-component").path()
    private val commandRegex by option("--pattern-command-regex").path()
    private val cext by option("--pattern-cext").path()

    private val disableBuiltinForMCJson by option("--disable-builtin-mcjson").flag()
    private val disableBuiltinForNbt by option("--disable-builtin-nbt").flag()
    private val disableBuiltinForCommand by option("--disable-builtin-command").flag()
    private val disableBuiltinForCommandData by option("--disable-builtin-command-data").flag()
    private val disableBuiltinForCommandComponent by option("--disable-builtin-command-component").flag()

    private val disableFilterForMCJson by option("--disable-filter-mcjson").flag()
    private val disableFilterForNbt by option("--disable-filter-nbt").flag()
    private val disableFilterForCommandData by option("--disable-filter-command-data").flag()

    context(_: FSHolder)
    private inline fun <reified T : Any> gatherPattern(
        path: Path?,
        disableBuiltin: Boolean,
        disableFilter: Boolean,
        builtin: T,
        merge: (T, T) -> T
    ): T? = when {
        disableFilter -> null
        path == null -> builtin.takeUnless { disableBuiltin }
        disableBuiltin -> path.readJson<T>()
        else -> merge(builtin, path.readJson<T>())
    }

    val pattern by lazy {
        MCTPattern(
            nbt = gatherPattern(
                nbt,
                disableBuiltinForNbt,
                disableFilterForNbt,
                BuiltinNbtPatterns
            ) { x, y -> x + y },
            mcjson = gatherPattern(
                mcjson,
                disableBuiltinForMCJson,
                disableFilterForMCJson,
                BuiltinMCJsonPatterns
            ) { x, y -> x + y },
            command = gatherPattern(
                command,
                disableBuiltinForCommand,
                false,
                BuiltinCommandPatterns
            ) { x, y -> x + y }
                ?: panic("Cannot use the `--disable-builtin-command` when no path to the pattern is passed"),
            commandData = gatherPattern(
                commandData,
                disableBuiltinForCommandData,
                disableFilterForCommandData,
                BuiltinCommandDataPatterns
            ) { x, y -> x + y },
            commandComponent = gatherPattern(
                commandComponent,
                disableBuiltinForCommandComponent,
                false,
                BuiltinMinecraftComponentPatterns
            ) { x, y -> x + y }
                ?: panic("Cannot use the `--disable-builtin-command-component` when no path to the pattern is passed"),
            commandRegex = commandRegex?.readJson() ?: emptyList(),
            cext = cext?.readJson(),
        )
    }
}

fun BaseCommand.printlnGreen(message: Any?) = terminal.println(TextColors.green(message.toString()))
fun BaseCommand.printlnYellow(message: Any?) = terminal.println(TextColors.yellow(message.toString()))
fun BaseCommand.printlnBlue(message: Any?) = terminal.println(TextColors.blue(message.toString()))
fun BaseCommand.printlnRed(message: Any?) = terminal.println(TextColors.red(message.toString()))