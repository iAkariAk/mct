package mct.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okio.FileSystem

actual val SystemFileSystem: FileSystem get() = unreachable
actual fun envvar(name: String): String? = unreachable
actual val Dispatchers.IO: CoroutineDispatcher get() = Dispatchers.Main
