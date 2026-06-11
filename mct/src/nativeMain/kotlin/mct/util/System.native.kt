@file:OptIn(ExperimentalForeignApi::class)

package mct.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import okio.FileSystem
import platform.posix.getenv

actual val SystemFileSystem get() = FileSystem.SYSTEM
actual fun envvar(name: String): String? = getenv(name)?.toKString()
actual val Dispatchers.IO get() = Dispatchers.IO