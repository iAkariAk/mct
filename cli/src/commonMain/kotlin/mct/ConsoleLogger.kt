package mct

import com.github.ajalt.clikt.core.BaseCliktCommand

class ConsoleLogger(
    private val ctx: BaseCliktCommand<*>,
    levels: List<LoggerLevel>
) : Logger(levels) {
    override fun log(level: LoggerLevel, message: String) {
        val isErr = level == LoggerLevel.Error
        ctx.echo("[$level] $message", err = isErr)
    }
}

