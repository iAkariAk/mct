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
    val raw: UIntArray
) {
    companion object {
        fun fromSource(source: BufferedSource): ChunkOffsetTable {
            val raw = UIntArray(Region.CHUNK_COUNT) {
                source.readInt().toUInt()
            }
            return ChunkOffsetTable(raw)
        }
    }

    init {
        require(raw.size == Region.CHUNK_COUNT)
    }

    inline fun forEach(block: (ChunkOffset) -> Unit) {
        raw.forEach { raw ->
            val offset = ChunkOffset(raw)
            block(offset)
        }
    }

    inline fun forEachIndexed(block: (index: Int, offset: ChunkOffset) -> Unit) {
        raw.forEachIndexed { index, raw ->
            val offset = ChunkOffset(raw)
            block(index, offset)
        }
    }

    inline fun necessarySectorCount(): UInt {
        var maxSectorCount = 2u
        for (rawOffset in raw) {
            val offset = ChunkOffset(rawOffset)
            if (offset.isEmpty()) continue
            val sectorOffset = offset.sectorOffset + offset.sectorUsedCount
            if (sectorOffset > maxSectorCount) {
                maxSectorCount = sectorOffset
            }
        }
        return maxSectorCount
    }

    fun writeTo(sink: BufferedSink) {
        forEach {
            it.writeTo(sink)
        }
    }

    fun chunkCount() = raw.count { it != ChunkOffset.EMPTY_RAW }

    operator fun get(index: Int) = ChunkOffset(raw[index])

    override fun toString() = "ChunkOffsetTable[...]"
}

@JvmInline
value class ChunkOffset(
    internal val raw: UInt,
) {
    val sectorOffset: UInt get() = raw shr 8 // Begin from 2
    val sectorUsedCount: UByte get() = (raw and 0xFFu).toUByte()

    companion object {
        val EMPTY_RAW = 0x00000000u
        val EMPTY = ChunkOffset(EMPTY_RAW)

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
    internal val raw: UIntArray,
) {
    companion object {
        fun fromSource(source: BufferedSource): TimestampTable {
            val raw = UIntArray(Region.CHUNK_COUNT) {
                source.readInt().toUInt()
            }
            return TimestampTable(raw)
        }
    }

    init {
        require(raw.size == Region.CHUNK_COUNT)
    }

    fun writeTo(sink: BufferedSink) {
        raw.forEach {
            sink.writeInt(it.toInt())
        }
    }

    operator fun get(index: Int) = raw[index]

    override fun toString() = "TimestampTable[...]"
}
