package mct.util.io

import okio.BufferedSource
import okio.FileSystem
import okio.Path
import okio.Path.Companion.DIRECTORY_SEPARATOR
import okio.Path.Companion.toPath

internal const val BUFFER_SIZE = 0x20000 // 128KiB

@Suppress("ObjectPropertyName")
private val _ROOT = "/".toPath()

val Path.Companion.ROOT: Path
    get() = _ROOT

fun List<String>.toPath(): Path = joinToString(DIRECTORY_SEPARATOR).toPath()
fun Path.appendAfterName(suffix: String): Path = (segments.subList(0, segments.size - 1) + (filename + suffix)).toPath()

// aka. nameWithoutExtension
val Path.filename get() = segments.lastOrNull()
val Path.stem get() = name.substringBeforeLast(".")
val Path.extension get() = name.substringAfterLast(".")
fun Path.startsWith(prefix: String) = name.endsWith(prefix)
fun Path.endsWith(suffix: String) = name.endsWith(suffix)

inline fun Path.readText(fs: FileSystem) = fs.read(this, BufferedSource::readUtf8)

context(fs: FileSystem)
inline fun Path.readText() = readText(fs)

inline fun Path.writeText(content: String, fs: FileSystem) = fs.write(this) {
    writeUtf8(content)
}

context(fs: FileSystem)
inline fun Path.writeText(content: String) = writeText(content, fs)

context(fs: FileSystem)
fun Path.copyToRecursively(target: Path) {
    if (fs.metadata(this).isDirectory) {
        fs.createDirectories(target)
        fs.list(this).forEach { entry ->
            entry.copyToRecursively(target / entry.name)
        }
    } else {
        fs.copy(this, target)
    }
}
