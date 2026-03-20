package mct.region.anvil

import arrow.core.raise.Raise
import arrow.core.raise.context.ensure
import mct.Env
import okio.Path
import okio.use

class RawRegionManager private constructor(
    env: Env,
    path: Path
) : RegionManager<RawRegion>(env, path) {
    companion object {
        context(_: Raise<ConstructionError>)
        operator fun invoke(env: Env, path: Path): RawRegionManager {
            val fs = env.fs
            ensure(fs.exists(path)) {
                ConstructionError.DirNotFound(path)
            }
            return RawRegionManager(env, path)
        }
    }

    context(_: Raise<LoadError>)
    override fun load(coord: Coord): RawRegion {
        val path = locate(coord)
        ensure(fs.exists(path)) {
            LoadError.FileNotFound(path)
        }
        val size = fs.metadata(path).size ?: -1
        ensure(size >= 8 * 1024) {
            LoadError.InvalidSize(size)
        }

        env.logger.debug { "Load region(${coord.x}, ${coord.z})" }

        return fs.openReadOnly(path).use { handle ->
            RawRegion.fromHandle(coord.x, coord.z, handle)
        }
    }

    context(_: Raise<SaveError>)
    override fun save(coord: Coord, region: RawRegion) {
        val path = locate(coord)
        ensure(fs.exists(path)) {
            SaveError.FileNotFound(path)
        }
        env.logger.debug { "Save region(${coord.x}, ${coord.z})" }
        fs.openReadWrite(path).use { handle ->
            region.writeTo(handle)
        }
    }
}
