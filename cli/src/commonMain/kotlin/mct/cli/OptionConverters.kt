package mct.cli

import com.github.ajalt.clikt.parameters.options.NullableOption
import com.github.ajalt.clikt.parameters.options.convert
import mct.FSHolder
import mct.serializer.MCTJson
import mct.util.io.readText
import okio.Path
import okio.Path.Companion.toPath

typealias NO = NullableOption<String, String>

fun NO.path() = convert { it.toPath() }

context(env: FSHolder)
fun Path?.fileText(default: String) = this?.readText(env.fs) ?: default

context(env: FSHolder)
inline fun <reified T : Any> Path?.jsonFile(default: T) = this?.readJson<T>() ?: default


context(env: FSHolder)
fun Path.fileText() = readText(env.fs)

context(env: FSHolder)
inline fun <reified T : Any> Path.jsonFile() = MCTJson.decodeFromString<T>(readText(env.fs))

