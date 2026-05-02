@file:OptIn(ExperimentalSerializationApi::class)

package mct.cli

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.json.okio.encodeToBufferedSink
import mct.FSHolder
import mct.serializer.PrettyJson
import mct.util.io.readText
import mct.util.io.writeText
import okio.Path

context(fs: FSHolder)
inline fun <reified T : Any> Path.readJson(): T =
    fs.fs.read(this) { PrettyJson.decodeFromBufferedSource<T>(this) }

context(fs: FSHolder)
inline fun <reified T : Any> Path.writeJson(data: T) =
    fs.fs.write(this) { PrettyJson.encodeToBufferedSink<T>(data, this) }

context(fs: FSHolder)
inline fun Path.readText() = readText(fs.fs)

context(fs: FSHolder)
inline fun Path.writeText(content: String) = writeText(content, fs.fs)
