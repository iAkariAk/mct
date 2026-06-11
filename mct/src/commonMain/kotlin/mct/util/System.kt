package mct.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okio.FileSystem

expect val SystemFileSystem: FileSystem

expect fun envvar(name: String): String?

expect val Dispatchers.IO: CoroutineDispatcher

