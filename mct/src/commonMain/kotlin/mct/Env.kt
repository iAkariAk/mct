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


context(env: Env)
val fs get() = env.fs

context(env: Env)
val logger get() = env.logger

