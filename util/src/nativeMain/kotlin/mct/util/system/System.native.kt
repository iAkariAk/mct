@file:OptIn(ExperimentalForeignApi::class)

package mct.util.system

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import okio.FileSystem
import platform.posix.getenv

actual val SystemFileSystem get() = FileSystem.SYSTEM
actual fun envvar(name: String): String? = getenv(name)?.toKString()