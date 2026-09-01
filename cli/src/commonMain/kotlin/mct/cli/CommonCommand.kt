package mct.cli

import arrow.core.getOrElse
import arrow.core.raise.Raise
import arrow.core.raise.either
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.BaseCliktCommand
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

context(_: FSHolder)
fun BaseCliktCommand<*>.withPattern(): Lazy<MCTPattern> {
    val mcjson = option("--pattern-mcjson-pattern").path().also(::registerOption)
    val nbt = option("--pattern-nbt-pattern").path().also(::registerOption)
    val command = option("--pattern-command").path().also(::registerOption)
    val commandData = option("--pattern-command-data").path().also(::registerOption)
    val commandComponent = option("--pattern-command-component").path().also(::registerOption)
    val commandRegex = option("--pattern-command-regex").path().also(::registerOption)
    val cext = option("--pattern-cext").path().also(::registerOption)

    val disableBuiltinForMCJson = option("--disable-builtin-mcjson").flag().also(::registerOption)
    val disableBuiltinForNbt = option("--disable-builtin-nbt").flag().also(::registerOption)
    val disableBuiltinForCommand = option("--disable-builtin-command").flag().also(::registerOption)
    val disableBuiltinForCommandData = option("--disable-builtin-command-data").flag().also(::registerOption)
    val disableBuiltinForCommandComponent = option("--disable-builtin-command-component").flag().also(::registerOption)

    val disableFilterForMCJson = option("--disable-filter-mcjson").flag().also(::registerOption)
    val disableFilterForNbt = option("--disable-filter-nbt").flag().also(::registerOption)
    val disableFilterForCommandData = option("--disable-filter-command-data").flag().also(::registerOption)
    return lazy {
        MCTPattern(
            nbt = gatherPattern(
                nbt.value,
                disableBuiltinForNbt.value,
                disableFilterForNbt.value,
                BuiltinNbtPatterns
            ) { x, y -> x + y },
            mcjson = gatherPattern(
                mcjson.value,
                disableBuiltinForMCJson.value,
                disableFilterForMCJson.value,
                BuiltinMCJsonPatterns
            ) { x, y -> x + y },
            command = gatherPattern(
                command.value,
                disableBuiltinForCommand.value,
                false,
                BuiltinCommandPatterns
            ) { x, y -> x + y }
                ?: panic("Cannot use the `--disable-builtin-command` when no path to the pattern is passed"),
            commandData = gatherPattern(
                commandData.value,
                disableBuiltinForCommandData.value,
                disableFilterForCommandData.value,
                BuiltinCommandDataPatterns
            ) { x, y -> x + y },
            commandComponent = gatherPattern(
                commandComponent.value,
                disableBuiltinForCommandComponent.value,
                false,
                BuiltinMinecraftComponentPatterns
            ) { x, y -> x + y }
                ?: panic("Cannot use the `--disable-builtin-command-component` when no path to the pattern is passed"),
            commandRegex = commandRegex.value?.readJson() ?: emptyList(),
            cext = cext.value?.readJson(),
        )
    }

}

fun BaseCommand.printlnGreen(message: Any?) = terminal.println(TextColors.green(message.toString()))
fun BaseCommand.printlnYellow(message: Any?) = terminal.println(TextColors.yellow(message.toString()))
fun BaseCommand.printlnBlue(message: Any?) = terminal.println(TextColors.blue(message.toString()))
fun BaseCommand.printlnRed(message: Any?) = terminal.println(TextColors.red(message.toString()))