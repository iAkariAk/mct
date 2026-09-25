@file:OptIn(ExperimentalSerializationApi::class)

package mct.util.io

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.json.okio.encodeToBufferedSink
import mct.FSHolder
import mct.serializer.MCTCbor
import mct.serializer.MCTJson
import mct.serializer.PrettyJson
import okio.Path


context(fs: FSHolder)
inline fun <reified T : Any> Path.readJson(): T =
    fs.fs.read(this) { PrettyJson.decodeFromBufferedSource<T>(this) }

context(fs: FSHolder)
inline fun <reified T : Any> Path.writeJson(data: T, pretty: Boolean = true) = fs.fs.write(this) {
    val format = if (pretty) PrettyJson else MCTJson
    format.encodeToBufferedSink<T>(data, this)
}

context(fs: FSHolder)
inline fun <reified T : Any> Path.readCbor(): T =
    fs.fs.read(this) { MCTCbor.decodeFromByteArray<T>(readByteArray()) }

context(fs: FSHolder)
inline fun <reified T : Any> Path.writeCbor(data: T) = fs.fs.write(this) {
    write(MCTCbor.encodeToByteArray<T>(data))
}

context(fs: FSHolder)
inline fun Path.readText() = readText(fs.fs)

context(fs: FSHolder)
inline fun Path.readBytes() = readBytes(fs.fs)

context(fs: FSHolder)
inline fun Path.writeText(content: String) = writeText(content, fs.fs)

context(fs: FSHolder)
inline fun Path.writeBytes(bytes: ByteArray) = writeBytes(bytes, fs.fs)

