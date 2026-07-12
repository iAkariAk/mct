package mct.cli.cmd.kits

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands


class Kit : SuspendingCliktCommand(name = "kit") {
    init {
        subcommands(ExportSnbt(), ExportScheme(), ReplaceAll(), TextPool(), TermExtract(), AITranslate(), MTLXKit())
    }

    override fun help(context: Context) = "Some helpful tool"

    override suspend fun run() = Unit
}
