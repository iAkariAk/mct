package mct.util.io

import no.synth.kmpzip.io.InputStream
import no.synth.kmpzip.io.OutputStream
import no.synth.kmpzip.okio.ZipInputStream
import no.synth.kmpzip.okio.ZipOutputStream
import no.synth.kmpzip.zip.ZipInputStream
import no.synth.kmpzip.zip.ZipOutputStream
import okio.*
import kotlin.math.min

fun FileSystem.openZipInputStream(file: Path) = ZipInputStream(source(file).buffer())
fun FileSystem.openZipOutputStream(file: Path) = ZipOutputStream(sink(file).buffer())

inline fun <T> FileSystem.readZip(file: Path, action: (ZipInputStream) -> T) =
    read(file) { action(ZipInputStream(this)) }

inline fun <T> FileSystem.writeZip(file: Path, action: (ZipOutputStream) -> T) =
    write(file) { action(ZipOutputStream(this)) }

fun InputStream.source(): Source {
    val inputStream = this
    return object : Source {
        private val buffer = ByteArray(8192)
        override fun read(sink: Buffer, byteCount: Long): Long {
            val byteCount = byteCount.toInt()
            val maxToRead = min(8192, byteCount)
            val len = inputStream.read(buffer, 0, maxToRead)
            if (len == -1) return -1L
            sink.write(buffer, 0, len)
            return len.toLong()
        }

        override fun timeout(): Timeout = Timeout.NONE

        override fun close() = inputStream.close()

    }
}

fun OutputStream.sink(): Sink {
    val outputStream = this
    return object : Sink {
        private val buffer = ByteArray(8192)

        override fun write(source: Buffer, byteCount: Long) {
            val byteCount = byteCount.toInt()
            var total = 0
            while (total < byteCount) {
                val maxToRead = min(byteCount - total, 8192)
                val len = source.read(buffer, 0, maxToRead)
                if (len == -1) throw EOFException()
                outputStream.write(buffer, 0, len)
                total += len
            }
        }

        override fun timeout(): Timeout = Timeout.NONE
        override fun flush() = outputStream.flush()
        override fun close() = outputStream.close()
    }
}

fun ZipInputStream.walk() = sequence {
    do {
        val zipEntry = nextEntry ?: break
        yield(zipEntry)
    } while (true)
}

