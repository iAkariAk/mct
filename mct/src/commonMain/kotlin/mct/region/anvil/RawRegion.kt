package mct.region.anvil

import kotlinx.serialization.decodeFromByteArray
import mct.region.anvil.ChunkOffset.Companion.ChunkOffset
import mct.region.anvil.Region.Companion.CHUNK_COUNT
import net.benwoodworth.knbt.NbtTag
import okio.FileHandle
import okio.buffer
import okio.use
import kotlin.math.min

private const val SECTOR_SIZE = 4096

class RawRegion(
    override val regionX: Int, // align with 32
    override val regionZ: Int, // align with 32
    override val offsets: ChunkOffsetTable,
    override val timestamps: TimestampTable,
    val chunks: List<RawChunk?>
) : Region {
    companion object {
        fun fromHandle(
            regionX: Int,
            regionY: Int,
            handle: FileHandle
        ): RawRegion {
            val offsets: ChunkOffsetTable
            val timestamps: TimestampTable
            handle.source(0).buffer().use { source ->
                offsets = ChunkOffsetTable.fromSource(source)
                timestamps = TimestampTable.fromSource(source)
            }
            val chunks = List(CHUNK_COUNT) { index ->
                val offset = offsets[index]
                if (offset.isEmpty()) return@List null
                val fileOffset = offset.sectorOffset.toLong() * SECTOR_SIZE
                if (fileOffset >= handle.size()) return@List null

                handle.source(fileOffset).buffer().use { source ->
                    val size = source.readInt()
                        .toUInt() // beginning from the 5th byte of this chunk, excludes self and compress kind byte
                    val compressKind = source.readByte()

                    val nbtSerializer = RawChunk.getNbtSerializer(compressKind)

                    val bytes = source.readByteArray(size.toLong())
                    val actualSectorByteCount = offset.sectorUsedCount.toLong() * SECTOR_SIZE
                    val usedSize = 5u + size
                    val paddingSize =
                        min(handle.size() - handle.position(source), actualSectorByteCount - usedSize.toLong())
                    source.skip(paddingSize)

                    val data = nbtSerializer.decodeFromByteArray<NbtTag>(bytes)
                    RawChunk(index.toUInt(), size, compressKind, data, bytes)
                }
            }
            return RawRegion(
                regionX,
                regionY,
                offsets,
                timestamps,
                chunks
            )
        }
    }

    fun inferFilename() = "r.$regionX.$regionZ.mca"

    fun writeTo(handle: FileHandle) {
        var chunkBeginPos: Long
        handle.sink().buffer().use { sink ->
            offsets.writeTo(sink)
            timestamps.writeTo(sink)
            chunkBeginPos = handle.position(sink) // must be 8192
        }
        val necessaryChunkCount = offsets.offsets.indexOfLast { !it.isEmpty() }
        val necessarySectorCount = offsets.offsets.sliceArray(0..necessaryChunkCount).sumOf { it.sectorUsedCount }
        handle.resize(chunkBeginPos + necessarySectorCount.toLong() * SECTOR_SIZE.toLong())

        offsets.offsets.forEachIndexed { index, offset ->
            val chunk = chunks[index] ?: return@forEachIndexed
            handle.write(offset.sectorOffset.toLong(), chunk.rawData, 0, chunk.rawData.size)
        }
    }

    fun modifyChunks(modified: List<RawChunk?>): RawRegion {
        var sectorOffsetOffset = 0u
        val newOffsets = Array(CHUNK_COUNT) { index ->
            val origin = offsets[index]
            val modified = modified[index] ?: return@Array ChunkOffset.EMPTY
            val sectorCount = calculateSectorCount(modified.rawData.size.toUInt())
            val diff = sectorCount - origin.sectorUsedCount
            if (diff > 0u) {
                sectorOffsetOffset += diff
            }
            ChunkOffset(origin.sectorOffset + sectorOffsetOffset, sectorCount)
        }
        return RawRegion(
            regionX = regionX,
            regionZ = regionZ,
            offsets = ChunkOffsetTable(newOffsets),
            timestamps = timestamps,
            chunks = modified
        )
    }

    inline fun modifyChunks(modify: (List<RawChunk?>) -> List<RawChunk?>) = modifyChunks(modify(chunks))

    override fun toString() = "Region(x=$regionX, y=$regionZ, chunkCount=${chunks.size})"
}

internal inline fun calculateSectorCount(usedByteCount: UInt): UByte =
    ((5u + usedByteCount + SECTOR_SIZE.toUInt() - 1u) / SECTOR_SIZE.toUInt()).toUByte() // ceil div