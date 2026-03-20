package mct.region.anvil

import arrow.core.raise.Raise
import arrow.core.raise.context.ensure
import kotlinx.serialization.KSerializer
import mct.Env
import mct.region.anvil.model.ChunkData
import mct.region.anvil.model.ChunkDataKind
import okio.Path
import kotlin.reflect.KType
import kotlin.reflect.typeOf


open class BaseRegion<T : ChunkData>(
    internal var raw: RawRegion,
    val kind: ChunkDataKind,
    identifier: KType
) : Region by raw {
    init {
        require(kind.isTypeOf(identifier))
    }

    companion object {
        inline operator fun <reified T : ChunkData> invoke(
            raw: RawRegion,
            kind: ChunkDataKind = ChunkDataKind.of<T>()
        ): BaseRegion<T> =
            BaseRegion(raw, kind = kind, typeOf<T>())
    }

    val data = Array(Region.CHUNK_COUNT) { index ->
        raw.chunks.getOrNull(index)?.let {
            @Suppress("UNCHECKED_CAST")
            val deserializer = it.nbtSerializer.serializersModule.run(kind.gettingSerializer) as KSerializer<T>

            ParsedChunk(
                it,
                dataDeserializer = deserializer,
            )
        }
    }

    fun modify(new: RawRegion) {
        raw = new
    }

    fun modifyChunks(modify: (index: Int, ParsedChunk<T>?) -> ParsedChunk<T>?) {
        val encodedData = data.mapIndexed { index, chunk ->
            modify(index, chunk)?.raw
        }
        raw = RawRegion(raw.regionX, raw.regionZ, raw.offsets, raw.timestamps, encodedData)
    }

    override fun toString() = "BaseRegion(pointer=${data.contentToString()})"
}

class BaseRegionManager<T : ChunkData> private constructor(
    val raw: RawRegionManager,
    val kind: ChunkDataKind,
    private val identifier: KType
) : RegionManager<BaseRegion<T>>(raw.env, raw.path) {
    companion object {
        context(_: Raise<ConstructionError>)
        operator fun <T : ChunkData> invoke(
            env: Env,
            path: Path,
            kind: ChunkDataKind,
            identifier: KType
        ): BaseRegionManager<T> {
            return invoke(RawRegionManager(env, path), kind, identifier)
        }


        context(_: Raise<ConstructionError>)
        operator fun <T : ChunkData> invoke(
            raw: RawRegionManager,
            kind: ChunkDataKind,
            identifier: KType
        ): BaseRegionManager<T> {
            ensure(raw.fs.exists(raw.path)) {
                ConstructionError.DirNotFound(raw.path)
            }
            return BaseRegionManager(raw, kind, identifier)
        }


        context(_: Raise<ConstructionError>)
        inline operator fun <reified T : ChunkData> invoke(
            raw: RawRegionManager,
            kind: ChunkDataKind = ChunkDataKind.of<T>()
        ): BaseRegionManager<T> =
            invoke(raw, kind = kind, typeOf<T>())

        context(_: Raise<ConstructionError>)
        inline operator fun <reified T : ChunkData> invoke(
            env: Env,
            path: Path,
            kind: ChunkDataKind = ChunkDataKind.of<T>()
        ): BaseRegionManager<T> =
            invoke(RawRegionManager(env, path), kind = kind, typeOf<T>())
    }

    context(_: Raise<LoadError>)
    override fun load(coord: Coord): BaseRegion<T> =
        BaseRegion(raw.load(coord), kind, identifier)

    context(_: Raise<SaveError>)
    override fun save(coord: Coord, region: BaseRegion<T>) =
        raw.save(coord, region.raw)
}