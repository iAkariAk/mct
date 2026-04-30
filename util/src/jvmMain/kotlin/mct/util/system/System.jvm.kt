package mct.util.system

import okio.FileSystem

actual val SystemFileSystem get() = FileSystem.SYSTEM
actual fun envvar(name: String): String? = System.getenv(name)