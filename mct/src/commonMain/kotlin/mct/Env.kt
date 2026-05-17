package mct

import mct.util.aio.AsyncFileSystem

interface LoggerHolder {
    val logger: Logger
}

interface FSHolder {
    val fs: AsyncFileSystem
}

interface EnvHolder : LoggerHolder, FSHolder {
    val env: Env

    override val fs get() = env.fs
    override val logger get() = env.logger
}

data class Env(
    override val fs: AsyncFileSystem,
    override val logger: Logger = Logger.None
) : EnvHolder, LoggerHolder, FSHolder {
    override val env get() = this
}


context(env: Env)
val fs get() = env.fs

context(env: Env)
val logger get() = env.logger

