package mct.cli.util

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import kotlinx.io.okio.asKotlinxIoRawSink
import mct.FSHolder
import mct.fs
import okio.*

private class ZeroByteSafeSink(private val delegate: Sink) : Sink by delegate {
    override fun write(source: Buffer, byteCount: Long) {
        if (byteCount > 0L) delegate.write(source, byteCount)
    }
}

context(_: FSHolder)
suspend fun HttpClient.downloadAndSha1(url: String, target: Path): String = prepareRequest(url).execute { response ->
    fs.sink(target).use { sink ->
        val hashingSink = HashingSink.sha1(sink)
        response.bodyAsChannel().readTo(ZeroByteSafeSink(hashingSink).asKotlinxIoRawSink())
        hashingSink.hash.hex()
    }
}

