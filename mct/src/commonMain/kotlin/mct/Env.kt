package mct

import okio.FileSystem
import okio.SYSTEM

interface LoggerHolder {
    val logger: Logger
}

interface FSHolder {
    val fs: FileSystem
}

interface EnvHolder : LoggerHolder, FSHolder {
    val env: Env

    override val fs get() = env.fs
    override val logger get() = env.logger
}

data class Env(
    override val fs: FileSystem = FileSystem.SYSTEM,
    override val logger: Logger = Logger.None
) : EnvHolder, LoggerHolder, FSHolder {
    override val env get() = this

    companion object {
        val Default = Env()
    }
}


context(env: Env)
val fs get() = env.fs

context(env: Env)
val logger get() = env.logger

