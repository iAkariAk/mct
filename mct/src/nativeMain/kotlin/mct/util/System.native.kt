@file:OptIn(ExperimentalForeignApi::class)

package mct.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import mct.util.aio.AsyncFileSystem
import mct.util.aio.zio
import platform.posix.getenv

val SystemFileSystem: AsyncFileSystem get() = okio.FileSystem.SYSTEM.zio()
actual fun envvar(name: String): String? = getenv(name)?.toKString()
