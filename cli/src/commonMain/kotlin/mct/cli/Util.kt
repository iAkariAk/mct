@file:OptIn(ExperimentalSerializationApi::class)

package mct.cli

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.json.okio.encodeToBufferedSink
import mct.serializer.PrettyJson
import okio.Path

context(env: EnvProvider)
inline fun <reified T : Any> Path.readJson(): T =
    env.fs.read(this) { PrettyJson.decodeFromBufferedSource<T>(this) }

context(env: EnvProvider)
inline fun <reified T : Any> Path.writeJson(data: T) =
    env.fs.write(this) { PrettyJson.encodeToBufferedSink<T>(data, this) }

