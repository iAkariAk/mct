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
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.mordant.rendering.TextColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import mct.*
import mct.cli.util.CURRENT_PATH
import mct.command.BuiltinCommandDataPatterns
import mct.command.BuiltinCommandPatterns
import mct.command.BuiltinMinecraftComponentPatterns
import mct.command.CommandExtractPattern
import mct.dp.compileWith
import mct.dp.mcjson.BuiltinMCJsonPatterns
import mct.nbt.BuiltinNbtPatterns
import mct.util.IO
import mct.util.SystemFileSystem
import mct.util.io.readJson
import mct.util.io.relativeToIfRelative
import mct.util.toRegex2
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
    val parallelism by option(
        "--parallelism",
        help = "The number of parallelism (Default: ${MCTConfig.Default.parallelism})"
    ).int()

    val workspace by lazy {
        either {
            val config = MCTConfig(
                parallelism = parallelism ?: MCTConfig.Default.parallelism,
            )
            MCTWorkspace(input, env, config)
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
            command = when {
                command.value == null -> BuiltinCommandPatterns.takeUnless { disableBuiltinForCommand.value }
                disableBuiltinForCommand.value ->
                    command.value!!.readJson<List<CommandExtractPattern>>().compileWith()

                else -> command.value!!.readJson<List<CommandExtractPattern>>()
                    .compileWith(BuiltinCommandPatterns)
            } ?: panic("Cannot use the `--disable-builtin-command` when no path to the pattern is passed"),
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

abstract class RegexMultiInputCommand(name: String, help: String) : BaseCommand(name, help) {
    abstract fun Path.correspondToOutput(outputDir: Path): Path?

    val input by option("--input", "-i", help = "Path to input file(s)").required()
    val regex by option("--regex", "-r", help = "Use regex to match input files").flag()
    val inputDir by option("--input-dir", "-id", help = "Path to input dir").path().default(Path.CURRENT_PATH)
    val outputFileOrDir by option("--output", "-o", help = "Path to output file or directory").path()
        .default(Path.CURRENT_PATH)

    context(_: Raise<MCTError>)
    override suspend fun App() {
        if (!regex) {
            val inputFile = input.toPath().relativeToIfRelative(inputDir)
            val outputFile = outputFileOrDir.takeUnless { fs.metadataOrNull(it)?.isDirectory == true }
                ?: inputFile.correspondToOutput(outputFileOrDir)
                ?: panic("Cannot infer the output path")
            output(inputFile, outputFile)
        } else {
            val regex = input.toRegex2()
            enforce(fs.metadata(outputFileOrDir).isDirectory) {
                "When using `--regex` to match file, your output must be a directory instead of file."
            }
            coroutineScope {
                fs.listRecursively(inputDir)
                    .filter { fs.metadata(it).isRegularFile && regex.matches(it.toString()) }
                    .forEach { file ->
                        launch(Dispatchers.IO) {
                            val inputFile = file.relativeToIfRelative(inputDir)
                            val outputFile = inputFile.correspondToOutput(
                                outputFileOrDir
                            ) ?: panic("Cannot infer the output file")
                            output(inputFile, outputFile)
                        }
                    }
            }
        }
    }

    abstract fun output(inputFile: Path, outputFile: Path)
}

fun BaseCommand.printlnGreen(message: Any?) = terminal.println(TextColors.green(message.toString()))
fun BaseCommand.printlnYellow(message: Any?) = terminal.println(TextColors.yellow(message.toString()))
fun BaseCommand.printlnBlue(message: Any?) = terminal.println(TextColors.blue(message.toString()))
fun BaseCommand.printlnRed(message: Any?) = terminal.println(TextColors.red(message.toString()))