package mct.cli

import com.github.ajalt.clikt.parameters.options.NullableOption
import com.github.ajalt.clikt.parameters.options.convert
import mct.serializer.MCTJson
import mct.util.io.readText
import okio.Path
import okio.Path.Companion.toPath

typealias NO = NullableOption<String, String>

fun NO.path() = convert { it.toPath() }

context(env: EnvProvider)
fun Path?.fileText(default: String) = this?.readText(env.fs) ?: default

context(env: EnvProvider)
inline fun <reified T : Any> Path?.jsonFile(default: T) = this?.readJson<T>() ?: default


context(env: EnvProvider)
fun Path.fileText() = readText(env.fs)

context(env: EnvProvider)
inline fun <reified T : Any> Path.jsonFile() = MCTJson.decodeFromString<T>(readText(env.fs))

