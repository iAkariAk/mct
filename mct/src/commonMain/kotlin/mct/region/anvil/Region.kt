@file:OptIn(ExperimentalUnsignedTypes::class)

package mct.region.anvil

import okio.BufferedSink
import okio.BufferedSource
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
        fun fromSource(source: BufferedSource): ChunkOffsetTable {
            val raw = Array(Region.Companion.CHUNK_COUNT) {
                val raw = source.readInt().toUInt()
                ChunkOffset(raw)
            }
            return ChunkOffsetTable(raw)
        }
    }

    init {
        require(offsets.size == Region.Companion.CHUNK_COUNT)
    }

    fun writeTo(sink: BufferedSink) {
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
    val sectorOffset: UInt get() = raw shr 8
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

    fun writeTo(sink: BufferedSink) {
        sink.writeInt(raw.toInt())
    }
}

@JvmInline
value class TimestampTable(
    internal val raw: UIntArray
) {
    companion object {
        fun fromSource(source: BufferedSource): TimestampTable {
            val raw = UIntArray(Region.Companion.CHUNK_COUNT) {
                source.readInt().toUInt()
            }
            return TimestampTable(raw)
        }
    }

    init {
        require(raw.size == Region.Companion.CHUNK_COUNT)
    }

    fun writeTo(sink: BufferedSink) {
        raw.forEach {
            sink.writeInt(it.toInt())
        }
    }

    operator fun get(index: Int) = raw[index]

    override fun toString() = "TimestampTable[...]"
}
