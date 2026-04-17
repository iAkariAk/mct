package mct.cli

import arrow.core.raise.Raise
import arrow.core.raise.context.raise
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.versionOption
import kotlinx.coroutines.runBlocking
import mct.MCTError
import mct.cli.cmd.Datapack
import mct.cli.cmd.Kit
import mct.cli.cmd.Region

fun main(args: Array<String>) = runBlocking {
    MCT().main(args)
}

class MCT : SuspendingCliktCommand("MCT") {
    init {
        versionOption("SNAPSHOT")
        subcommands(Datapack(), Region(), Kit())
    }

    override suspend fun run() = Unit
}


data class Panic(override val message: String) : MCTError

context(_: Raise<MCTError>)
inline fun panic(message: String): Nothing = raise(Panic(message))