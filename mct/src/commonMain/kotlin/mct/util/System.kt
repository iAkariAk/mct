package mct.util

import okio.FileSystem

expect val SystemFileSystem: FileSystem

expect fun envvar(name: String): String?
