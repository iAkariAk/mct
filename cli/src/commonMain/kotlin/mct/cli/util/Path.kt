package mct.cli.util

import mct.FSHolder
import mct.cli.panic
import mct.fs
import okio.Path
import okio.Path.Companion.toPath

val Path.Companion.CURRENT_PATH get() = ".".toPath(true)

context(_: FSHolder)
fun requirePath(path: String, label: String): Path {
    val p = path.toPath()
    if (!fs.exists(p)) panic("$label file not found: $path")
    return p
}
