package mct.util.system

import okio.FileSystem

expect val SystemFileSystem: FileSystem

expect fun envvar(name: String): String?
