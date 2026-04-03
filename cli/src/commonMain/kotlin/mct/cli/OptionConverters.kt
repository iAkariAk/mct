package mct.cli

import com.github.ajalt.clikt.parameters.options.NullableOption
import com.github.ajalt.clikt.parameters.options.convert
import mct.serializer.MCTJson
import mct.util.io.readText
import okio.Path.Companion.toPath

typealias NO = NullableOption<String, String>

fun NO.path() = convert { it.toPath() }

context(cmd: BaseCommand)
fun NO.fileText() = convert { it.toPath().readText(cmd.env.fs) }

context(cmd: BaseCommand)
inline fun <reified T : Any> NO.jsonFile() = fileText().convert {
    MCTJson.decodeFromString<T>(it)
}
