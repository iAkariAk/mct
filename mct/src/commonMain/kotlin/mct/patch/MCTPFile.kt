@file:OptIn(ExperimentalSerializationApi::class)

package mct.patch

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import dev.karmakrafts.kompress.Compressor
import dev.karmakrafts.kompress.compressingSink
import dev.karmakrafts.kompress.deflate.Deflater
import dev.karmakrafts.kompress.zlib.*
import kotlinx.io.buffered
import kotlinx.io.okio.asKotlinxIoRawSink
import kotlinx.io.okio.asKotlinxIoRawSource
import kotlinx.io.readByteArray
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import mct.FSHolder
import mct.MCTError
import mct.fs
import mct.model.patch.Patch
import mct.patch.MCTPFile.CURRENT_VERSION
import mct.patch.MCTPFile.MAGIC_NUMBER
import okio.BufferedSink
import okio.BufferedSource
import okio.Path

sealed interface MCTPFileError : MCTError {
    data class MagicMismatch(val actual: Int) : MCTPFileError {
        override val message =
            "Magic number mismatch: expect ${MAGIC_NUMBER.toHexString()}, but got ${actual.toHexString()}"
    }

    data class VersionMismatch(val actual: Int) : MCTPFileError {
        override val message =
            "Version mismatch: expect $CURRENT_VERSION, but got $actual"
    }
}

object MCTPFile {
    const val MAGIC_NUMBER = 0x4d435450 // MCTP in ASCII
    const val CURRENT_VERSION = 1

    context(_: Raise<MCTPFileError>)
    fun decodeFromSource(source: BufferedSource): Patch {
        val source = source.asKotlinxIoRawSource().buffered()
        val magic = source.readInt()
        ensure(magic == MAGIC_NUMBER) { MCTPFileError.MagicMismatch(magic) }
        val version = source.readInt()
        ensure(version == CURRENT_VERSION) { MCTPFileError.VersionMismatch(version) }
        val raw = source.unzlibSource().buffered().readByteArray()
        return ProtoBuf.decodeFromByteArray<Patch>(raw)
    }

    context(_: FSHolder, _: Raise<MCTPFileError>)
    fun decodeFromFile(path: Path): Patch =
        fs.read(path) { decodeFromSource(this) }


    fun encodeToSink(sink: BufferedSink, patch: Patch) {
        sink.flush()

        val sink = sink.asKotlinxIoRawSink().buffered()
        sink.writeInt(MAGIC_NUMBER)
        sink.writeInt(CURRENT_VERSION)
        sink.flush()
        sink.compressingSink(
            compressor = ZlibCompressor(
                Deflater.MAX_LEVEL,
                ZlibCMF(),
                ZlibFlags(ZlibCompressionLevel.fromDeflaterLevel(Deflater.MAX_LEVEL))
            ),
            bufferSize = Compressor.DEFAULT_BUFFER_SIZE,
            isSinkOwned = false
        ).buffered().use { zlibSink ->
            zlibSink.write(ProtoBuf.encodeToByteArray(patch))
        }
    }


    context(_: FSHolder)
    fun encodeToFile(path: Path, patch: Patch) = fs.write(path, false) {
        encodeToSink(this, patch)
    }
}