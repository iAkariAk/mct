package mct

import arrow.core.raise.context.Raise
import arrow.core.raise.context.either
import arrow.core.raise.context.ensure
import mct.region.anvil.*
import mct.serializer.NbtGzip
import net.benwoodworth.knbt.NbtCompound
import net.benwoodworth.knbt.decodeFromSource
import okio.Path
import okio.Path.Companion.toPath

sealed interface OpenError : MCTError {
    data class UnvalidatedDir(val dir: Path) : OpenError {
        override val message = "There isn't level.dat founded in $dir"
    }
}

class MCTWorkspace private constructor(
    val rootDir: Path,
    override val env: Env
) : EnvHolder {
    companion object {
        context(_: Raise<OpenError>)
        operator fun invoke(rootDir: Path, env: Env): MCTWorkspace {
            ensure (env.fs.exists(rootDir / "level.dat")) {
                OpenError.UnvalidatedDir(rootDir)
            }
            return MCTWorkspace(rootDir, env)
        }
    }

    val level =
        fs.read(rootDir / "level.dat".toPath()) {
            val rootTag = NbtGzip.decodeFromSource<NbtCompound>(this)
//            NbtGzip.decodeFromNbtTag<LevelRoot>(rootTag)
            // for backwards compatibility, use NbtTag rather but deserialization
            rootTag
        }

    val datapackDir = rootDir / "datapacks"

    val dimensions: DimensionProvider = DimensionProviderV1(this)
}

interface DimensionProvider : Map<String, Dimension>

class Dimension(
    internal val workspace: MCTWorkspace,
    val id: String,
    val path: Path
) {
    val poiDir = path / "poi"
    val regionDir = path / "region"
    val entitiesDir = path / "entities"

    private inline fun <T> raiseAsNull(
        action: context(Raise<ConstructionError>) () -> T
    ) = either { action() }.getOrNull()

    val regionRawMgr = raiseAsNull { RawRegionManager(workspace.env, regionDir) }
    val poiRawMgr = raiseAsNull { RawRegionManager(workspace.env, poiDir) }
    val entitiesRawMgr = raiseAsNull { RawRegionManager(workspace.env, entitiesDir) }
    val regionMgr = raiseAsNull { regionRawMgr?.let { TerrainRegionManager(it) } }
    val poiMgr = raiseAsNull { regionRawMgr?.let { PoiRegionManager(it) } }
    val entitiesMgr = raiseAsNull { regionRawMgr?.let { EntitiesRegionManager(it) } }

    override fun toString(): String {
        return "Dimension(path=$path, id='$id')"
    }
}

private class DimensionProviderV1(workspace: MCTWorkspace) : DimensionProvider, Map<String, Dimension> by mapOf(
    "minecraft:nether" to Dimension(workspace, "minecraft:nether", workspace.rootDir / "DIM-1"),
    "minecraft:overworld" to Dimension(workspace, "minecraft:overworld", workspace.rootDir),
    "minecraft:the_end" to Dimension(workspace, "minecraft:the_end", workspace.rootDir / "DIM1"),
) {
    override fun toString() = "DimensionProviderV1"
}