package mct.region.anvil

import arrow.core.Either
import kotlinx.serialization.KSerializer
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.serializer
import mct.region.anvil.model.ChunkData
import mct.serializer.NbtGzip
import mct.serializer.NbtNone
import mct.serializer.NbtZlib
import mct.util.NonLazyNull
import net.benwoodworth.knbt.NbtTag
import okio.BufferedSink


sealed interface Chunk

class RawChunk(
    val index: Int,
    val compressKind: Byte,
    val rawData: ByteArray,
) : Chunk {
    companion object {
        const val COMPRESS_GZIP: Byte = 1
        const val COMPRESS_ZLIB: Byte = 2
        const val COMPRESS_NONE: Byte = 3
        const val COMPRESS_LZ4: Byte = 4

        internal val NonLazyNull = NonLazyNull<RawChunk>()

        internal fun getNbtSerializer(kind: Byte) = when (kind) {
            COMPRESS_GZIP -> NbtGzip
            COMPRESS_ZLIB -> NbtZlib
            COMPRESS_NONE -> NbtNone
            else -> error("Unknow compress kind: $kind")
        }

        fun stringifyCompressKind(kind: Byte) = when (kind) {
            COMPRESS_GZIP -> "GZIP"
            COMPRESS_ZLIB -> "ZLIB"
            COMPRESS_NONE -> "NONE"
            else -> "UNKNOW"
        }
    }

    init {
        require(compressKind in 1..3)
    }

    val size = 5 + rawData.size

    val nbtSerializer get() = getNbtSerializer(compressKind)

    val data by lazy {
        Either.catch {
            nbtSerializer.decodeFromByteArray<NbtTag>(rawData)
        }
    }

    inline fun modify(modify: (NbtTag?) -> NbtTag?): RawChunk {
        val tag = data.getOrNull()
        val modified = modify(tag)
        if (tag === modified) return this
        val modifiedBytes = modified?.let(nbtSerializer::encodeToByteArray) ?: rawData
        return RawChunk(index, compressKind, modifiedBytes)
    }

    fun writeTo(sink: BufferedSink): Long {
        sink.writeInt(1 + rawData.size)
        sink.writeByte(compressKind.toInt())
        sink.write(rawData)
        return 5 + rawData.size.toLong()
    }

    override fun toString(): String {
        return "Chunk(size=$size, compressKind=${stringifyCompressKind(compressKind)})"
    }
}

class ParsedChunk<T : ChunkData> private constructor(
    val raw: RawChunk,
    val decodedData: T?,
    internal val dataDeserializer: KSerializer<T>,
) : Chunk {
    constructor(raw: RawChunk, dataDeserializer: KSerializer<T>) : this(
        raw,
        raw.data.getOrNull()?.let { raw.nbtSerializer.decodeFromNbtTag(dataDeserializer, it) },
        dataDeserializer
    )

    companion object {
        inline operator fun <reified T : ChunkData> invoke(raw: RawChunk): ParsedChunk<T> =
            ParsedChunk(raw, raw.nbtSerializer.serializersModule.serializer())
    }


    fun modify(modify: (T?) -> T?): ParsedChunk<T> {
        val new = modify(decodedData)
        val modifiedChunk = raw.modify {
            new?.let {
                raw.nbtSerializer.encodeToNbtTag(dataDeserializer, new)
            }
        }
        return ParsedChunk(modifiedChunk, new, dataDeserializer)
    }

    override fun toString() = "ParsedChunk(decodedData=$decodedData)"
}