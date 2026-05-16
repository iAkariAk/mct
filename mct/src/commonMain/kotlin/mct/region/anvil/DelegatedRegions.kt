package mct.region.anvil

import arrow.core.raise.Raise
import arrow.core.raise.context.ensure
import mct.region.anvil.model.ChunkDataKind
import mct.region.anvil.model.EntitiesChunkData
import mct.region.anvil.model.PoiChunkData
import mct.region.anvil.model.TerrainChunkData

class TerrainRegion(
    internal val base: BaseRegion<TerrainChunkData>
) : Region by base {
    val data get() = base.data
}

class TerrainRegionManager(
    private val delegate: BaseRegionManager<TerrainChunkData>
) : RegionManager<TerrainRegion>(delegate.env, delegate.path) {
    context(_: Raise<LoadError>)
    override fun load(coord: Coord): TerrainRegion = TerrainRegion(delegate.load(coord))

    context(_: Raise<SaveError>)
    override fun save(coord: Coord, region: TerrainRegion) = delegate.save(coord, region.base)
}

context(_: Raise<ConstructionError>)
fun TerrainRegionManager(raw: RawRegionManager): TerrainRegionManager {
    ensure(raw.fs.exists(raw.path)) {
        ConstructionError.DirNotFound(raw.path)
    }
    return TerrainRegionManager(BaseRegionManager(raw, ChunkDataKind.Terrain))
}


class EntitiesRegion(
    internal val base: BaseRegion<EntitiesChunkData>
) : Region by base {
    val data get() = base.data
}

class EntitiesRegionManager(
    private val delegate: BaseRegionManager<EntitiesChunkData>
) : RegionManager<EntitiesRegion>(delegate.env, delegate.path) {
    context(_: Raise<LoadError>)
    override fun load(coord: Coord): EntitiesRegion = EntitiesRegion(delegate.load(coord))

    context(_: Raise<SaveError>)
    override fun save(coord: Coord, region: EntitiesRegion) = delegate.save(coord, region.base)
}

context(_: Raise<ConstructionError>)
fun EntitiesRegionManager(raw: RawRegionManager): EntitiesRegionManager {
    ensure(raw.fs.exists(raw.path)) {
        ConstructionError.DirNotFound(raw.path)
    }
    return EntitiesRegionManager(BaseRegionManager(raw, ChunkDataKind.Entities))
}



class PoiRegion(
    internal val base: BaseRegion<PoiChunkData>
) : Region by base {
    val data get() = base.data
}

class PoiRegionManager(
    private val delegate: BaseRegionManager<PoiChunkData>
) : RegionManager<PoiRegion>(delegate.env, delegate.path) {
    context(_: Raise<LoadError>)
    override fun load(coord: Coord): PoiRegion = PoiRegion(delegate.load(coord))

    context(_: Raise<SaveError>)
    override fun save(coord: Coord, region: PoiRegion) = delegate.save(coord, region.base)
}

context(_: Raise<ConstructionError>)
fun PoiRegionManager(raw: RawRegionManager): PoiRegionManager {
    ensure(raw.fs.exists(raw.path)) {
        ConstructionError.DirNotFound(raw.path)
    }
    return PoiRegionManager(BaseRegionManager(raw, ChunkDataKind.Poi))
}
