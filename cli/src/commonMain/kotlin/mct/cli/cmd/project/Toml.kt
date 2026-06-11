package mct.cli.cmd.project

import com.akuleshov7.ktoml.Toml
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import mct.FSHolder
import mct.util.io.readText
import mct.util.io.writeText
import okio.Path

context(fs: FSHolder)
inline fun <reified T : Any> Path.readToml(): T =
    fs.fs.read(this) { Toml.decodeFromString<T>(readText()) }

context(fs: FSHolder)
inline fun <reified T : Any> Path.writeToml(data: T) =
    writeText(Toml.encodeToString<T>(data))

