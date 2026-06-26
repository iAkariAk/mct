package mct

import arrow.core.raise.context.Raise
import arrow.core.raise.context.either
import arrow.core.raise.context.ensure
import mct.model.DataVersions
import mct.model.LevelRoot
import mct.region.anvil.*
import mct.serializer.NbtGzip
import mct.util.toSnbt
import net.benwoodworth.knbt.NbtCompound
import net.benwoodworth.knbt.decodeFromNbtTag
import net.benwoodworth.knbt.decodeFromSource
import okio.Path
import okio.Path.Companion.toPath

sealed interface OpenError : MCTError {
    data class UnvalidatedDir(val dir: Path) : OpenError {
        override val message = "There isn't level.dat founded in $dir"
    }
}

class MCTWorkspace private constructor(
    val rootDir: Path, override val env: Env,
) : EnvHolder {
    companion object {
        context(_: Raise<OpenError>)
        operator fun invoke(rootDir: Path, env: Env): MCTWorkspace {
            ensure(env.fs.exists(rootDir / "level.dat")) {
                OpenError.UnvalidatedDir(rootDir)
            }
            return MCTWorkspace(rootDir, env)
        }
    }

    val levelRaw = fs.read(rootDir / "level.dat".toPath()) {
        val rootTag = NbtGzip.decodeFromSource<NbtCompound>(this)
        rootTag
    }
    val level: LevelRoot? = runCatching {
        NbtGzip.decodeFromNbtTag<LevelRoot>(levelRaw)
    }.getOrElse {
        logger.warning { "Cannot parse level root: $it" }
        null
    }

    val datapackDir = rootDir / "datapacks"

    val dimensions: DimensionProvider = run {
        logger.info { "Minecraft version ${level?.data?.versionInfo?.toSnbt()}(${level?.data?.dataVersion})" }

        val dataVersion = level?.data?.dataVersion
        val useV2 = if (dataVersion != null) {
            dataVersion >= DataVersions.`26_1-snapshot-6`
        } else {
            fs.exists(rootDir / "dimensions")
        }

        if (useV2) {
            logger.info { "Use DimensionProviderV2" }
            DimensionProviderV2(this)
        } else {
            logger.info { "Use DimensionProviderV1" }
            DimensionProviderV1(this)
        }
    }

}

interface DimensionProvider : Map<String, Dimension>

class Dimension(
    internal val workspace: MCTWorkspace, val id: String, val path: Path,
) {
    val poiDir = path / "poi"
    val regionDir = path / "region"
    val entitiesDir = path / "entities"

    private inline fun <T> raiseAsNull(
        action: context(Raise<ConstructionError>) () -> T,
    ) = either { action() }.getOrNull()

    val regionRawMgr = raiseAsNull { RawRegionManager(workspace.env, regionDir) }
    val poiRawMgr = raiseAsNull { RawRegionManager(workspace.env, poiDir) }
    val entitiesRawMgr = raiseAsNull { RawRegionManager(workspace.env, entitiesDir) }
    val regionMgr = raiseAsNull { regionRawMgr?.let { TerrainRegionManager(it) } }
    val poiMgr = raiseAsNull { poiRawMgr?.let { PoiRegionManager(it) } }
    val entitiesMgr = raiseAsNull { entitiesRawMgr?.let { EntitiesRegionManager(it) } }

    override fun toString(): String {
        return "Dimension(path=$path, id='$id')"
    }
}

private fun MutableMap<String, Dimension>.scanCustomizedDimensions(workspace: MCTWorkspace) {
    val dimDir = workspace.rootDir / "dimensions"
    val fs = workspace.fs
    val logger = workspace.logger
    if (!fs.exists(dimDir)) return
    val outerDimensions =
        fs.list(dimDir).asSequence()
            .filter { it.name != "minecraft" }
            .map { it.name to fs.list(it).map { it.name to it } }
    outerDimensions.forEach { (namespace, innerDimensions) ->
        innerDimensions.forEach { (name, path) ->
            val id = "$namespace:$name"
            logger.info { "Find dimension $id at $path" }
            val dim = Dimension(workspace, id, path)
            put(id, dim)
        }
    }
}

private class DimensionProviderV1(workspace: MCTWorkspace) : DimensionProvider, Map<String, Dimension> by (buildMap {
    put("minecraft:nether", Dimension(workspace, "minecraft:nether", workspace.rootDir / "DIM-1"))
    put("minecraft:overworld", Dimension(workspace, "minecraft:overworld", workspace.rootDir))
    put("minecraft:the_end", Dimension(workspace, "minecraft:the_end", workspace.rootDir / "DIM1"))

    scanCustomizedDimensions(workspace)
}) {
    override fun toString() = "DimensionProviderV1"
}


// before 26.1-snapshot-6
private class DimensionProviderV2(workspace: MCTWorkspace) : DimensionProvider, Map<String, Dimension> by (buildMap {
    fun minecraft(name: String) {
        val id = "minecraft:$name"
        put(id, Dimension(workspace, id, workspace.rootDir / "dimensions" / "minecraft" / name))
    }

    scanCustomizedDimensions(workspace)

    minecraft("nether")
    minecraft("overworld")
    minecraft("the_end")
}) {
    override fun toString() = "DimensionProviderV2"
}