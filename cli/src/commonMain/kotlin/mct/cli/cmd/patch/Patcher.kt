package mct.cli.cmd.patch

import arrow.core.raise.Raise
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import mct.MCTError
import mct.cli.WorkspaceCommand

class Patcher : SuspendingCliktCommand(name = "patch") {
    init {
        subcommands(

        )
    }

    override fun help(context: Context) = "Patch kit"

    override suspend fun run() = Unit
}


private class ApplyPatch : WorkspaceCommand(name = "apply") {

    context(_: Raise<MCTError>)
    override suspend fun App() {

    }
}