package mct

import mct.util.SystemFileSystem
import okio.FileSystem

interface LoggerHolder {
    val logger: Logger
}

interface NotifierHolder {
    val notifier: Notifier
}

interface FSHolder {
    val fs: FileSystem
}

interface VersionHolder {
    companion object {
        val None = object : VersionHolder {
            override val dataVersion = null
        }

        inline fun of(dataVersion: Int) = object : VersionHolder {
            override val dataVersion = dataVersion
        }
    }

    val dataVersion: Int?
}

interface EnvHolder : LoggerHolder, NotifierHolder, FSHolder {
    val env: Env

    override val fs get() = env.fs
    override val notifier get() = env.notifier
    override val logger get() = env.logger
}

data class Env(
    override val fs: FileSystem = SystemFileSystem,
    override val logger: Logger = Logger.None,
    override val notifier: Notifier = Notifier.None,
) : EnvHolder, LoggerHolder, NotifierHolder, FSHolder {
    override val env get() = this

    companion object {
        val Default = Env()
    }
}


context(holder: EnvHolder)
val env get() = holder.env

context(holder: FSHolder)
val fs get() = holder.fs

context(holder: LoggerHolder)
val logger get() = holder.logger

context(holder: NotifierHolder)
val notifier get() = holder.notifier

context(holder: VersionHolder)
val dataVersion get() = holder.dataVersion


context(_: VersionHolder)
inline fun checkDataVersion(
    lower: Int,
    ifNone: () -> Unit = {},
    ifLess: (Int) -> Unit = {}
) {
    val version = dataVersion
    when {
        version == null -> ifNone()
        version <= lower -> ifLess(version)
    }
}

context(_: VersionHolder, _: LoggerHolder)
inline fun checkDataVersion(
    lower: Int,
    ifLess: (Int) -> Unit = {}
) = checkDataVersion(lower, {
    logger.warning { "Unknown data version maybe cause some problems." }
}, ifLess)