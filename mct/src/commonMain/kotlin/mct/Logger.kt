package mct

import kotlinx.serialization.json.Json

enum class LoggerLevel {
    Info,
    Debug,
    Error,
    Warning,
    Sign;

    val mask = 1 shl ordinal

    companion object {
        val Verbose = listOf(Info, Debug, Error, Warning)
    }
}

abstract class Logger(
    val enabledLevels: List<LoggerLevel>
) {
    companion object {
        val None = object : Logger(emptyList()) {
            override fun log(level: LoggerLevel, message: String) = Unit
            override fun toString() = "Logger.None"
        }

        fun Console(levels: List<LoggerLevel> = LoggerLevel.Verbose) = object : Logger(levels) {
            override fun log(level: LoggerLevel, message: String) {
                println("[$level] $message")
            }

            override fun toString() = "Logger.Console"
        }
    }

    val levelMarks = enabledLevels.fold(0) { acc, e ->
        acc or e.mask
    }

    abstract fun log(level: LoggerLevel, message: String)

    inline fun logFiltered(level: LoggerLevel, message: () -> String) {
        if ((level.mask and levelMarks) != 0) log(level, message())
    }

    inline fun info(message: () -> String) = logFiltered(LoggerLevel.Info, message)
    inline fun debug(message: () -> String) = logFiltered(LoggerLevel.Debug, message)
    inline fun error(message: () -> String) = logFiltered(LoggerLevel.Error, message)
    inline fun warning(message: () -> String) = logFiltered(LoggerLevel.Warning, message)

    inline fun <reified T> sign(value: () -> T) = logFiltered(LoggerLevel.Sign) {
        val key = keyOf<T>()
        val value = Json.encodeToString(value())
        "$key $value"
    }
}

inline fun <reified T> Logger.onSign(crossinline callback: (T) -> Unit): Logger = object : Logger(enabledLevels) {
    override fun log(level: LoggerLevel, message: String) {
        val orig = this@onSign
        if (level == LoggerLevel.Sign) {
            val (key, value) = message.split(" ", limit = 2)
            val expectedKey = keyOf<T>()
            if (expectedKey == key) {
                val msg = Json.decodeFromString<T>(value)
                callback(msg)
            }
        }
        orig.log(level, message)
    }
}

inline fun <reified T> keyOf() = (T::class.qualifiedName ?: T::class.toString()).hashCode().toString()