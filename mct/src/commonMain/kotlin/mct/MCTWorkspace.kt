package mct

import arrow.core.raise.context.Raise
import arrow.core.raise.context.either
import arrow.core.raise.context.ensure
import mct.model.DataVersions
import mct.model.LevelRoot
import mct.region.anvil.*
import mct.serializer.NbtGzip
import mct.util.aio.AsyncBuffer
import mct.util.aio.SuspendLazy
import mct.util.toSnbt
import net.benwoodworth.knbt.NbtCompound
import net.benwoodworth.knbt.decodeFromNbtTag
import okio.Path
import okio.Path.Companion.toPath

sealed interface OpenError : MCTError {
    data class UnvalidatedDir(val dir: Path) : OpenError {
        override val message = "There isn't level.dat founded in $dir"
    }
}

class MCTWorkspace private constructor(
    val rootDir: Path,
    override val env: Env,
    val levelRaw: NbtCompound,
    val level: LevelRoot?,
    val datapackDir: Path,
    val dimensions: DimensionProvider,
) : EnvHolder {
    companion object {
        context(_: Raise<OpenError>)
        suspend operator fun invoke(rootDir: Path, env: Env): MCTWorkspace {
            ensure(env.fs.exists(rootDir / "level.dat")) {
                OpenError.UnvalidatedDir(rootDir)
            }

            val levelRaw = env.fs.read(rootDir / "level.dat".toPath()) {
                val buf = AsyncBuffer()
                readAll(buf)
                NbtGzip.decodeFromSource(NbtCompound.serializer(), buf.okioBuffer)
            }

            val level: LevelRoot? = runCatching {
                NbtGzip.decodeFromNbtTag<LevelRoot>(levelRaw)
            }.getOrElse {
                env.logger.warning { "Cannot parse level root: $it" }
                null
            }

            val datapackDir = rootDir / "datapacks"

            val dimensions = buildDimensions(env, rootDir, level)

            return MCTWorkspace(rootDir, env, levelRaw, level, datapackDir, dimensions)
        }

        private suspend fun buildDimensions(env: Env, rootDir: Path, level: LevelRoot?): DimensionProvider {
            env.logger.info { "Minecraft version ${level?.data?.versionInfo?.toSnbt()}(${level?.data?.dataVersion})" }

            val dataVersion = level?.data?.dataVersion
            val useV2 = if (dataVersion != null) {
                dataVersion >= DataVersions.`26_1-snapshot-6`
            } else {
                env.fs.exists(rootDir / "dimensions")
            }

            return if (useV2) {
                env.logger.info { "Use DimensionProviderV2" }
                DimensionProviderV2(env, rootDir)
            } else {
                env.logger.info { "Use DimensionProviderV1" }
                DimensionProviderV1(env, rootDir)
            }
        }
    }
}

interface DimensionProvider : Map<String, Dimension>

class Dimension(
    internal val env: Env,
    val id: String,
    val path: Path
) {
    val poiDir = path / "poi"
    val regionDir = path / "region"
    val entitiesDir = path / "entities"

    private suspend inline fun <T> raiseAsNull(
        action: suspend context(Raise<ConstructionError>) () -> T
    ) = either { action() }.getOrNull()

    private val _regionRawMgr = SuspendLazy { raiseAsNull { RawRegionManager(env, regionDir) } }
    private val _poiRawMgr = SuspendLazy { raiseAsNull { RawRegionManager(env, poiDir) } }
    private val _entitiesRawMgr = SuspendLazy { raiseAsNull { RawRegionManager(env, entitiesDir) } }
    private val _regionMgr = SuspendLazy { regionRawMgr()?.let { raiseAsNull { TerrainRegionManager(it) } } }
    private val _poiMgr = SuspendLazy { poiRawMgr()?.let { raiseAsNull { PoiRegionManager(it) } } }
    private val _entitiesMgr = SuspendLazy { entitiesRawMgr()?.let { raiseAsNull { EntitiesRegionManager(it) } } }

    suspend fun regionRawMgr() = _regionRawMgr()
    suspend fun poiRawMgr() = _poiRawMgr()
    suspend fun entitiesRawMgr() = _entitiesRawMgr()
    suspend fun regionMgr() = _regionMgr()
    suspend fun poiMgr() = _poiMgr()
    suspend fun entitiesMgr() = _entitiesMgr()

    override fun toString(): String {
        return "Dimension(path=$path, id='$id')"
    }
}

private class DimensionProviderV1(
    env: Env,
    rootDir: Path
) : DimensionProvider, Map<String, Dimension> by mapOf(
    "minecraft:nether" to Dimension(env, "minecraft:nether", rootDir / "DIM-1"),
    "minecraft:overworld" to Dimension(env, "minecraft:overworld", rootDir),
    "minecraft:the_end" to Dimension(env, "minecraft:the_end", rootDir / "DIM1"),
)

private fun dim(rootDir: Path, name: String) = rootDir / "dimensions" / "minecraft" / name

private class DimensionProviderV2(
    env: Env,
    rootDir: Path
) : DimensionProvider, Map<String, Dimension> by mapOf(
    "minecraft:nether" to Dimension(env, "minecraft:nether", dim(rootDir, "the_nether")),
    "minecraft:overworld" to Dimension(env, "minecraft:overworld", dim(rootDir, "overworld")),
    "minecraft:the_end" to Dimension(env, "minecraft:the_end", dim(rootDir, "the_end")),
)
