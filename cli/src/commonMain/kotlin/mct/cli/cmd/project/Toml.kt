package mct.cli.cmd.project

import com.akuleshov7.ktoml.Toml
import com.akuleshov7.ktoml.TomlInputConfig
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import mct.FSHolder
import mct.util.io.readText
import mct.util.io.writeText
import okio.Path

val MCTToml = Toml(
    inputConfig = TomlInputConfig(
        ignoreUnknownNames = true
    )
)

context(fs: FSHolder)
inline fun <reified T : Any> Path.readToml(): T =
    fs.fs.read(this) { MCTToml.decodeFromString<T>(readText()) }

context(fs: FSHolder)
inline fun <reified T : Any> Path.writeToml(data: T) =
    writeText(MCTToml.encodeToString<T>(data))

