@file:OptIn(ExperimentalSerializationApi::class)

package mct.util.io

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.okio.encodeToBufferedSink
import mct.FSHolder
import mct.serializer.MCTJson
import mct.serializer.PrettyJson
import okio.Path

context(fs: FSHolder)
suspend inline fun <reified T : Any> Path.readJson(): T {
    val text = readText()
    return PrettyJson.decodeFromString<T>(text)
}

context(fs: FSHolder)
suspend inline fun <reified T : Any> Path.writeJson(data: T, pretty: Boolean = true) {
    fs.fs.write(this) {
        val format = if (pretty) PrettyJson else MCTJson
        format.encodeToBufferedSink(data, buffer().okioBuffer)
    }
}

context(fs: FSHolder)
suspend fun Path.readText() = readText(fs.fs)

context(fs: FSHolder)
suspend fun Path.writeText(content: String) = writeText(content, fs.fs)
