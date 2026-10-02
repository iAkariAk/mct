@file:OptIn(ExperimentalUnsignedTypes::class)

package mct.region.anvil

import mct.region.anvil.ChunkOffset.Companion.ChunkOffset
import mct.region.anvil.RawRegion.Companion.SECTOR_SIZE
import mct.region.anvil.Region.Companion.CHUNK_COUNT
import mct.util.divCeil
import okio.FileHandle
import okio.IOException
import okio.buffer
import okio.use
import kotlin.time.Clock

class RawRegion internal constructor(
    override val regionX: Int, // align with 32
    override val regionZ: Int, // align with 32
    override val offsets: ChunkOffsetTable,
    override val timestamps: TimestampTable,
    val chunks: List<RawChunk?>
) : Region {
    companion object {
        const val SECTOR_SIZE = 4096
        val EMTPY_SECTOR = ByteArray(SECTOR_SIZE)

        fun fromHandle(
            regionX: Int,
            regionZ: Int,
            handle: FileHandle
        ): RawRegion = handle.source().buffer().use { source ->
            val offsets = ChunkOffsetTable.fromSource(source)
            val timestamps = TimestampTable.fromSource(source)
            val fileSize = handle.size()
            val chunks = List(CHUNK_COUNT) { index ->
                val offset = offsets[index]
                if (offset.isEmpty()) return@List null
                val fileOffset = offset.sectorOffset.toLong() * SECTOR_SIZE
                if (fileOffset >= fileSize) return@List null

                handle.reposition(source, fileOffset)
                // beginning from the 5th byte of this chunk (i.e. compressKind), excludes self but includes compressKind
                val size = source.readInt()
                require(size >= 0) { "Illegal negative chunk size $size" }
                val actualSectorByteCount = offset.sectorUsedCount.toLong() * SECTOR_SIZE
                val usedSize = 4 + size.toLong()
                require(usedSize <= actualSectorByteCount) {
                    "Chunk size($usedSize) exceeds allocated sectors($actualSectorByteCount)"
                }

                val compressKind = source.readByte()

                val bytes = try {
                    source.readByteArray(size.toLong() - 1)
                } catch (_: IOException) {
                    return@List null
                }

                // padding: min(handle.size() - handle.position(source), actualSectorByteCount - usedSize)

                RawChunk(index, compressKind, bytes)
            }
            return RawRegion(
                regionX,
                regionZ,
                offsets,
                timestamps,
                chunks
            )
        }
    }

    fun inferFilename() = "r.$regionX.$regionZ.mca"

    fun writeTo(handle: FileHandle) = handle.sink().buffer().use { sink ->
        offsets.writeTo(sink)
        timestamps.writeTo(sink)
        val necessarySectorCount = offsets.necessarySectorCount().toLong()
        handle.resize(necessarySectorCount * SECTOR_SIZE)

        offsets.forEachIndexed { index, offset ->
            if (offset.isEmpty()) return@forEachIndexed
            val chunk = chunks[index] ?: return@forEachIndexed
            handle.reposition(sink, offset.sectorOffset.toLong() * SECTOR_SIZE)
            chunk.writeTo(sink)
        }
    }

    fun modifyChunks(modified: List<RawChunk?>): RawRegion {
        require(modified.size == CHUNK_COUNT) {
            "Chunk count only is $CHUNK_COUNT"
        }

        var currentSector = 2u // header
        val newTimestamps = timestamps.raw.copyOf()
        val currentTimestamps = Clock.System.now().epochSeconds.toUInt()
        val newOffsets = UIntArray(CHUNK_COUNT) { index ->
            val modifiedChunk = modified[index] ?: return@UIntArray ChunkOffset.EMPTY_RAW
            val currentChunk = chunks[index]

            if (modifiedChunk !== currentChunk) {
                newTimestamps[index] = currentTimestamps
            }

            val sectorCount = calculateSectorCountForChunk(modifiedChunk.size)

            ChunkOffset(currentSector, sectorCount).also {
                currentSector += sectorCount
            }.raw
        }


        return RawRegion(
            regionX = regionX,
            regionZ = regionZ,
            offsets = ChunkOffsetTable(newOffsets),
            timestamps = TimestampTable(newTimestamps),
            chunks = modified
        )
    }

    inline fun modifyChunks(modify: (List<RawChunk?>) -> List<RawChunk?>) = modifyChunks(modify(chunks))

    override fun toString() = "Region(x=$regionX, z=$regionZ, chunkCount=${chunks.size})"
}

internal inline fun calculateSectorCountForChunk(chunkSize: Int): UByte =
    (chunkSize divCeil SECTOR_SIZE).toUByte()