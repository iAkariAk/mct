package mct.cli

import arrow.continuations.SuspendApp
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.parse
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.versionOption
import mct.cli.cmd.datapack.Datapack
import mct.cli.cmd.kits.Kit
import mct.cli.cmd.project.Project
import mct.cli.cmd.region.Region
import mct.cli.cmd.test.Test

fun main(args: Array<String>) = SuspendApp {
    val command = MCT()
    try {
        command.parse(args)
    } catch (e: CliktError) {
        command.echoFormattedHelp(e)
        if (e.statusCode != 0) throw e
    }
}

class MCT : SuspendingCliktCommand("MCT") {
    init {
        versionOption("SNAPSHOT")
        subcommands(Datapack(), Region(), Kit(), Project(), Test())
    }

    override suspend fun run() = Unit
}


class Panic(message: String) : Throwable(message)

inline fun panic(message: String): Nothing = throw Panic(message)
