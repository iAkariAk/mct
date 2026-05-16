@file:OptIn(ExperimentalSerializationApi::class)

package mct.util.io

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.json.okio.encodeToBufferedSink
import mct.FSHolder
import mct.serializer.MCTJson
import mct.serializer.PrettyJson
import okio.Path


context(fs: FSHolder)
inline fun <reified T : Any> Path.readJson(): T =
    fs.fs.read(this) { PrettyJson.decodeFromBufferedSource<T>(this) }

context(fs: FSHolder)
inline fun <reified T : Any> Path.writeJson(data: T, pretty: Boolean = true) =
    fs.fs.write(this) {
        val format = if (pretty) PrettyJson else MCTJson
        format.encodeToBufferedSink<T>(data, this)
    }

context(fs: FSHolder)
inline fun Path.readText() = readText(fs.fs)

context(fs: FSHolder)
inline fun Path.writeText(content: String) = writeText(content, fs.fs)
