@file:OptIn(ExperimentalSerializationApi::class)

package mct.patch

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import kotlinx.serialization.ExperimentalSerializationApi
import mct.FSHolder
import mct.MCTError
import mct.fs
import mct.model.patch.Patch
import mct.patch.MCTPFile.CURRENT_VERSION
import mct.patch.MCTPFile.MAGIC_NUMBER
import mct.serializer.NbtZlib
import net.benwoodworth.knbt.Nbt
import net.benwoodworth.knbt.decodeFromSource
import net.benwoodworth.knbt.encodeToSink
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

private val MctpNbt = Nbt(NbtZlib) {
    compressionLevel = 9
}

object MCTPFile {
    const val MAGIC_NUMBER = 0x4d435450 // MCTP in ASCII
    const val CURRENT_VERSION = 1

    context(_: Raise<MCTPFileError>)
    fun decodeFromSource(source: BufferedSource): Patch {
        val magic = source.readInt()
        ensure(magic == MAGIC_NUMBER) { MCTPFileError.MagicMismatch(magic) }
        val version = source.readInt()
        ensure(version == CURRENT_VERSION) { MCTPFileError.VersionMismatch(version) }
        return MctpNbt.decodeFromSource(source)
    }

    context(_: FSHolder, _: Raise<MCTPFileError>)
    fun decodeFromFile(path: Path): Patch =
        fs.read(path) { decodeFromSource(this) }


    fun encodeToSink(sink: BufferedSink, patch: Patch) {
        sink.writeInt(MAGIC_NUMBER)
        sink.writeInt(CURRENT_VERSION)
        MctpNbt.encodeToSink(patch, sink)
    }


    context(_: FSHolder)
    fun encodeToFile(path: Path, patch: Patch) = fs.write(path, false) {
        encodeToSink(this, patch)
    }
}