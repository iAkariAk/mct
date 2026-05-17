package mct.util

import mct.util.aio.AsyncFileSystem
import mct.util.aio.zio

val SystemFileSystem: AsyncFileSystem get() = okio.FileSystem.SYSTEM.zio()
actual fun envvar(name: String): String? = System.getenv(name)
