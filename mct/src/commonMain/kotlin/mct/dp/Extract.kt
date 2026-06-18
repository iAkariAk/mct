package mct.dp

import arrow.core.getOrElse
import arrow.core.raise.Raise
import arrow.core.raise.context.either
import arrow.core.raise.nullable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import mct.*
import mct.command.CommandExtractPattern
import mct.dp.mcfunction.MCFunctionExtractor
import mct.dp.mcjson.MCJsonExtractor
import mct.util.IO
import mct.util.io.*
import okio.FileSystem
import okio.Path
import mct.command.BuiltinMCFPatterns as MCFBuiltinPatterns

fun List<CommandExtractPattern>.compile(): Map<String, List<CommandExtractPattern>> =
    MCFBuiltinPatterns + groupBy { it.command }.toMap()

fun MCTWorkspace.extractFromDatapack(
    pattern: MCTPattern = MCTPattern.Default
): Flow<DatapackExtractionGroup> {
    if (pattern.mcjson == null) logger.warning { "The filter was disabled, which causes export all string out of mcjson from the datapack" }
    logger.info { "Scanning datapacks in $datapackDir" }

    val extractors = buildMap {
        fun add(constructExtractor: (pattern: MCTPattern) -> Extractor) {
            val extractor = constructExtractor(pattern)
            put(extractor.targetExtension, constructExtractor(pattern))
        }
        add(::MCFunctionExtractor)
        add(::MCJsonExtractor)
    }

    return fs.list(datapackDir)
        .asFlow()
        .mapNotNull {
            nullable {
                val metadata = fs.metadata(it)
                val sfs = if (metadata.isDirectory) {
                    fs.newRelativeFS(it)
                } else if (it.endsWith(".zip")) {
                    fs.openZipReadOnly(it)
                } else null
                sfs.bind() to it
            }
        }.flatMapMerge { (sfs, sourcePath) ->
            flow {
                sfs.useAsync { sfs ->
                    sfs.listRecursively(Path.ROOT)
                        .asFlow()
                        .filter { "__MACOSX" !in it.toString() }
                        .mapNotNull { zpath ->
                            nullable {
                                zpath to extractors[zpath.extension].bind()
                            }
                        }
                        .flatMapMerge { (zpath, extractor) ->
                            either {
                                env.logger.debug {
                                    "Extracting $zpath via $extractor"
                                }
                                flowOf(extractor.extract(env, sourcePath, sfs, zpath))
                            }.getOrElse { error ->
                                env.logger.error { error.message }
                                emptyFlow()
                            }
                        }
                        .filter { it.extractions.isNotEmpty() }
                        .collect { emit(it) }
                }
            }
        }.flowOn(Dispatchers.IO)
}


internal interface Extractor {
    val targetExtension: String

    context(_: Raise<ExtractError>, _: EnvHolder)
    fun extract(
        env: Env,
        sourcePath: Path,
        zfs: FileSystem,
        zpath: Path
    ): DatapackExtractionGroup
}

internal fun Extractor(
    name: String,
    targetExtension: String,
    extract: context(Raise<ExtractError>, EnvHolder) (
        env: Env,
        sourcePath: Path,
        zfs: FileSystem,
        zpath: Path
    ) -> DatapackExtractionGroup
) = object : Extractor {
    override val targetExtension = targetExtension

    context(_: Raise<ExtractError>, _: EnvHolder)
    override fun extract(
        env: Env,
        sourcePath: Path,
        zfs: FileSystem,
        zpath: Path
    ): DatapackExtractionGroup = extract(env, sourcePath, zfs, zpath)

    override fun toString() = "Extractor($name)"
}