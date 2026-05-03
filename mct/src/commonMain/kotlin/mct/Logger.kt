package mct


import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import mct.util.BuilderMaker
import kotlin.reflect.KClass

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

    val levelMarks = (enabledLevels + LoggerLevel.Sign).fold(0) { acc, e ->
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
        val key = T::class.qualifiedName ?: T::class.toString()
        val value = Json.encodeToString(value())
        "$key $value"
    }
}

data class RegistryItem<T : Any>(
    val clazz: KClass<T>,
    val callback: (T) -> Unit,
) {
    @Suppress("UNCHECKED_CAST")
    operator fun invoke(data: Any) = callback(data as T)

    val key = clazz.qualifiedName ?: clazz.toString()

    val serializer = clazz.serializer()
}

@BuilderMaker
interface OnSignRegistry {
    fun <T : Any> on(clazz: KClass<T>, callback: (T) -> Unit)
}

inline fun <reified T : Any> OnSignRegistry.on(noinline callback: (T) -> Unit) = on(T::class, callback)

inline fun Logger.onSign(register: OnSignRegistry.() -> Unit): Logger {
    val hooks = mutableMapOf<String, RegistryItem<*>>()

    val registry = object : OnSignRegistry {
        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> on(clazz: KClass<T>, callback: (T) -> Unit) {
            hooks[clazz.qualifiedName ?: clazz.toString()] = RegistryItem(clazz, callback as (Any) -> Unit)
        }
    }
    registry.register()
    return object : Logger(enabledLevels) {
        override fun log(level: LoggerLevel, message: String) {
            val orig = this@onSign
            if (level == LoggerLevel.Sign) {
                val (key, value) = message.split(" ", limit = 2)
                hooks[key]?.let { item ->
                    val data = Json.decodeFromString(item.serializer, value)
                    item.invoke(data)
                }
            } else orig.log(level, message)
        }
    }
}

