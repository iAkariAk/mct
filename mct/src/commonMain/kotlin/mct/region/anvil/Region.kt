@file:OptIn(ExperimentalUnsignedTypes::class)

package mct.region.anvil

import mct.util.aio.AsyncBufferedSink
import mct.util.aio.AsyncBufferedSource
import kotlin.jvm.JvmInline

sealed interface Region {
    val regionX: Int // align with 32
    val regionZ: Int // align with 32
    val offsets: ChunkOffsetTable
    val timestamps: TimestampTable

    companion object {
        const val CHUNK_COUNT = 1024 // 32 * 32
    }
}

@JvmInline
value class ChunkOffsetTable(
    val offsets: Array<ChunkOffset>
) {
    companion object {
        suspend fun fromSource(source: AsyncBufferedSource): ChunkOffsetTable {
            val raw = Array(Region.Companion.CHUNK_COUNT) {
                val raw = source.readInt().toUInt()
                ChunkOffset(raw)
            }
            return ChunkOffsetTable(raw)
        }
    }

    init {
        require(offsets.size == Region.CHUNK_COUNT)
    }

    suspend fun writeTo(sink: AsyncBufferedSink) {
        offsets.forEach {
            it.writeTo(sink)
        }
    }

    fun chunkCount() = offsets.count { !it.isEmpty() }

    operator fun get(index: Int) = offsets[index]

    override fun toString() = "ChunkOffsetTable[...]"
}

@JvmInline
value class ChunkOffset(
    internal val raw: UInt,
) {
    val sectorOffset: UInt get() = raw shr 8 // Begin from 2
    val sectorUsedCount: UByte get() = (raw and 0xFFu).toUByte()

    companion object {
        val EMPTY = ChunkOffset(0x00000000u)

        inline fun ChunkOffset(sectorOffset: UInt, sectorUsedCount: UByte): ChunkOffset {
            require(sectorOffset <= 0xFFFFFFu) // uint24

            return ChunkOffset((sectorOffset shl 8) or sectorUsedCount.toUInt())
        }
    }

    /**
     * Uncreated chunk
     */
    fun isEmpty() = raw == 0u

    suspend fun writeTo(sink: AsyncBufferedSink) {
        sink.writeInt(raw.toInt())
    }
}

@JvmInline
value class TimestampTable(
    internal val raw: UIntArray
) {
    companion object {
        suspend fun fromSource(source: AsyncBufferedSource): TimestampTable {
            val raw = UIntArray(Region.Companion.CHUNK_COUNT) {
                source.readInt().toUInt()
            }
            return TimestampTable(raw)
        }
    }

    init {
        require(raw.size == Region.Companion.CHUNK_COUNT)
    }

    suspend fun writeTo(sink: AsyncBufferedSink) {
        raw.forEach {
            sink.writeInt(it.toInt())
        }
    }

    operator fun get(index: Int) = raw[index]

    override fun toString() = "TimestampTable[...]"
}
