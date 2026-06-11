package mct.util

import kotlinx.coroutines.Dispatchers
import okio.FileSystem

actual val SystemFileSystem get() = FileSystem.SYSTEM
actual fun envvar(name: String): String? = System.getenv(name)

actual val Dispatchers.IO get() = Dispatchers.IO