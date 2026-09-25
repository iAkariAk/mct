package mct.cli.cmd.kits

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands


class KitCommands : SuspendingCliktCommand(name = "kit") {
    init {
        subcommands(
            ExportSnbtCommand(),
            ExportSchemeCommand(),
            ReplaceAllCommand(),
            TextPoolCommands(),
            TermExtractCommand(),
            AITranslateCommand(),
            MTLXKitCommands(),
            OfficialLangCommands(),
            DisplayCommand(),
            ConvertCommand(),
            MapCommands()
        )
    }

    override fun help(context: Context) = "Some helpful tool"

    override suspend fun run() = Unit
}
