@file:OptIn(ExperimentalSerializationApi::class)

package mct.cli

import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.io.decodeFromSource
import kotlinx.serialization.json.io.encodeToSink
import mct.serializer.PrettyJson
import okio.FileSystem
import okio.Path

expect val SystemFileSystem: FileSystem

expect fun envvar(name: String): String?


context(env: EnvProvider)
inline fun <reified T : Any> Path.readJson(): T =
    env.fs.read(this) { PrettyJson.decodeFromSource<T>(this as Source) }

context(env: EnvProvider)
inline fun <reified T : Any> Path.writeJson(data: T) =
    env.fs.write(this) { PrettyJson.encodeToSink<T>(data, this as Sink) }

