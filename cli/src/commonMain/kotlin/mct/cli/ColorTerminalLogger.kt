package mct.cli

import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.terminal.Terminal
import mct.Logger
import mct.LoggerLevel

class ColorTerminalLogger(
    levels: List<LoggerLevel>,
) : Logger(levels) {
    private val terminal = Terminal()
    override fun log(level: LoggerLevel, message: String) {
        val color = when (level) {
            LoggerLevel.Info -> TextColors.cyan
            LoggerLevel.Debug -> TextColors.gray
            LoggerLevel.Error -> TextColors.red
            LoggerLevel.Warning -> TextColors.yellow
            LoggerLevel.Sign -> TextColors.magenta
        }
        val prefix = when (level) {
            LoggerLevel.Info -> "[INFO]"
            LoggerLevel.Debug -> "[DEBUG]"
            LoggerLevel.Error -> "[ERROR]"
            LoggerLevel.Warning -> "[WARN]"
            LoggerLevel.Sign -> "[SIGN]"
        }.let {
            TextStyles.bold(it)
        }

        terminal.println(color("$prefix $message"))
    }
}

