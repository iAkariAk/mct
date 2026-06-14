package mct.cli

import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.terminal.Terminal
import mct.Logger
import mct.LoggerLevel

val LoggerLevel.color
    get() = when (this) {
        LoggerLevel.Info -> TextColors.cyan
        LoggerLevel.Debug -> TextColors.gray
        LoggerLevel.Error -> TextColors.red
        LoggerLevel.Warning -> TextColors.yellow
    }

val LoggerLevel.prefix
    get() = when (this) {
        LoggerLevel.Info -> "[INFO]"
        LoggerLevel.Debug -> "[DEBUG]"
        LoggerLevel.Error -> "[ERROR]"
        LoggerLevel.Warning -> "[WARN]"
    }

class ColorTerminalLogger(
    levels: List<LoggerLevel>,
) : Logger(levels) {
    private val terminal = Terminal()
    override fun log(level: LoggerLevel, message: String) {
        val prefix = TextStyles.bold(level.prefix)

        terminal.println(level.color("$prefix $message"))
    }
}

