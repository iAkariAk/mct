package mct.region.anvil

import arrow.core.raise.Raise
import arrow.core.raise.context.either
import arrow.core.raise.nullable
import kotlinx.serialization.Serializable
import mct.Env
import mct.util.io.filename
import okio.Path

@Serializable
data class Coord(
    val x: Int,
    val z: Int
)

abstract class RegionManager<T : Region>(
    internal val env: Env,
    internal val path: Path
) {
    internal val fs get() = env.fs

    init {
        require(fs.exists(path))
    }

    fun locate(coord: Coord): Path = path / "r.${coord.x}.${coord.z}.mca"

    fun coords(): Sequence<Coord> = fs.list(path).asSequence()
        .mapNotNull { it.filename }
        .filter { it.endsWith(".mca") && it.startsWith("r.") }
        .map { it.removeSurrounding("r.", ".mca") }
        .map { it.split(".", limit = 2) }
        .mapNotNull { (x, y) ->
            nullable {
                Coord(x.toIntOrNull().bind(), y.toIntOrNull().bind())
            }
        }

    /**
     * Note: the region will be skipped if the load fails
     */
    fun regions(): Sequence<T> = coords().mapNotNull { coord ->
        either { load(coord) }.onLeft {
            env.logger.info { "(${path}) Skip region(${coord.x}, ${coord.z}) due to load error: ${it.message}" }
        }.getOrNull()
    }

    context(_: Raise<AnvilError>)
    fun modify(coord: Coord, modify: (T) -> T) {
        val data = load(coord)
        save(coord, modify(data))
    }

    context(_: Raise<AnvilError>)
    fun modifyRegions(modify: (T) -> T) {
        regions().forEach {
            save(Coord(it.regionX, it.regionZ), it)
        }
    }

    context(_: Raise<LoadError>)
    abstract fun load(coord: Coord): T

    context(_: Raise<SaveError>)
    abstract fun save(coord: Coord, region: T)
}

