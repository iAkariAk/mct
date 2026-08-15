@file:OptIn(DelicateCoroutinesApi::class)

package mct.cli

import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
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
    private val logScope = CoroutineScope(createLogDispatcher())
    private val logs = Channel<String>(Channel.UNLIMITED)

    init {
        logScope.launch {
            logs.consumeEach {
                terminal.println(it)
            }
        }
    }

    override fun log(level: LoggerLevel, message: String) {
        val prefix = TextStyles.bold(level.prefix)

        val coloredMessage = level.color("$prefix $message")
        logs.trySend(coloredMessage)
    }
}

expect fun createLogDispatcher(): CoroutineDispatcher