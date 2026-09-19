package mct.cli

import arrow.continuations.SuspendApp
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.mordant.platform.MultiplatformSystem.exitProcess
import mct.cli.cmd.cext.CextCommands
import mct.cli.cmd.datapack.DatapackCommands
import mct.cli.cmd.kits.KitCommands
import mct.cli.cmd.kits.PatchCommands
import mct.cli.cmd.project.ProjectCommands
import mct.cli.cmd.region.RegionCommands
import mct.cli.cmd.test.TestCommands

// calling `exitProcess` in CoroutineScope will cause deadlock
fun main(args: Array<String>) = SuspendApp(uncaught = ::handleUncaught) {
    MCT().main(args)
}

private fun handleUncaught(error: Throwable) {
    if (error is CliExit) exitProcess(error.statusCode)
    else error.printStackTrace()
}

class MCT : SuspendingCliktCommand("MCT") {
    init {
        configureContext {
            exitProcess = { statusCode -> throw CliExit(statusCode) }
        }
        versionOption("SNAPSHOT")
        subcommands(
            DatapackCommands(),
            RegionCommands(),
            CextCommands(),
            KitCommands(),
            ProjectCommands(),
            PatchCommands(),
            TestCommands()
        )
    }

    override suspend fun run() = Unit
}


class CliExit(val statusCode: Int) : RuntimeException("Exit with status $statusCode")

class Panic(message: String) : Throwable(message)

inline fun panic(message: String): Nothing = throw Panic(message)
inline fun enforce(value: Boolean, message: () -> String) {
    if (!value) panic(message())
}

inline fun enforceNotNull(value: Any?, message: () -> String) {
    if (value == null) panic(message())
}