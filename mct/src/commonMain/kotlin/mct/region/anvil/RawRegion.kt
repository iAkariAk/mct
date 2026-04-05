@file:OptIn(ExperimentalUnsignedTypes::class)

package mct.region.anvil

import kotlinx.serialization.decodeFromByteArray
import mct.region.anvil.ChunkOffset.Companion.ChunkOffset
import mct.region.anvil.RawRegion.Companion.SECTOR_SIZE
import mct.region.anvil.Region.Companion.CHUNK_COUNT
import mct.util.divCeil
import net.benwoodworth.knbt.NbtTag
import okio.FileHandle
import okio.IOException
import okio.buffer
import okio.use
import kotlin.math.min
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
                    val size = source.readInt() // beginning from the 5th byte of this chunk (i.e. compressKind), excludes self but includes compressKind
                    require(size >= 0) { "Illegal negative chunk size $size" }
                    val actualSectorByteCount = offset.sectorUsedCount.toLong() * SECTOR_SIZE
                    val usedSize = 4 + size.toLong()
                    require(usedSize <= actualSectorByteCount) {
                        "Chunk size($usedSize) exceeds allocated sectors($actualSectorByteCount)"
                    }

                    val compressKind = source.readByte()
                    val nbtSerializer = RawChunk.getNbtSerializer(compressKind)

                    val bytes = try {
                        source.readByteArray(size.toLong() - 1)
                    } catch (_: IOException) {
                        return@List null
                    }

                    val paddingSize =
                        min(handle.size() - handle.position(source), actualSectorByteCount - usedSize)
                    source.skip(paddingSize)

                    val data = try {
                        nbtSerializer.decodeFromByteArray<NbtTag>(bytes)
                    } catch (_: IOException) {
                        return@List null
                    }
                    RawChunk(index, compressKind, data, bytes)
                }
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

    fun writeTo(handle: FileHandle) {
        handle.sink().buffer().use { sink ->
            offsets.writeTo(sink)
            timestamps.writeTo(sink)
        }
        val necessarySectorCount = offsets.offsets
            .asSequence()
            .filter { !it.isEmpty() }
            .maxOfOrNull { it.sectorOffset + it.sectorUsedCount }?.toLong()
            ?: 2

        handle.resize(necessarySectorCount * SECTOR_SIZE)

        offsets.offsets.forEachIndexed { index, offset ->
            if (offset.isEmpty()) return@forEachIndexed
            val chunk = chunks[index] ?: return@forEachIndexed
            handle.sink(offset.sectorOffset.toLong() * SECTOR_SIZE).buffer()
                .use(chunk::writeTo)
        }
    }

    fun modifyChunks(modified: List<RawChunk?>): RawRegion {
        require(modified.size == CHUNK_COUNT) {
            "Chunk count only is $CHUNK_COUNT"
        }

        var currentSector = 2u // header
        val newTimestamps = timestamps.raw.copyOf()
        val currentTimestamps = Clock.System.now().epochSeconds.toUInt()
        val newOffsets = Array(CHUNK_COUNT) { index ->
            val chunk = modified[index] ?: return@Array ChunkOffset.EMPTY

            newTimestamps[index] = currentTimestamps

            val sectorCount = calculateSectorCountForChunk(chunk.size)

            ChunkOffset(currentSector, sectorCount).also {
                currentSector += sectorCount
            }
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