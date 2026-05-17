package mct.dp

import arrow.core.getOrElse
import arrow.core.raise.Raise
import arrow.core.raise.context.either
import arrow.core.raise.nullable
import kotlinx.coroutines.flow.*
import mct.DatapackExtractionGroup
import mct.Env
import mct.MCTWorkspace
import mct.dp.mcfunction.BuiltinMCFunctionDataPatterns
import mct.dp.mcfunction.ExtractPattern
import mct.dp.mcfunction.ExtractPatternSet
import mct.dp.mcfunction.MCFunctionExtractor
import mct.dp.mcjson.MCJsonExtractor
import mct.pointer.DataPointerPattern
import mct.util.DispatcherOrIO
import mct.util.aio.AsyncFileSystem
import mct.util.io.*
import okio.Path
import mct.dp.mcfunction.BuiltinMCFPatterns as MCFBuiltinPatterns
import mct.dp.mcjson.BuiltinMCJPatterns as MCJsonBuiltinPatterns

suspend fun MCTWorkspace.extractFromDatapack(
    mcfPatterns: List<ExtractPattern> = emptyList(),
    mcfDataPatterns: List<DataPointerPattern>? = emptyList(),
    mcjPatterns: List<DataPointerPattern>? = emptyList()
) = extractFromDatapackRaw(
    mcfPatterns.compile(),
    mcfDataPatterns?.let { BuiltinMCFunctionDataPatterns + mcfDataPatterns },
    mcjPatterns?.let { MCJsonBuiltinPatterns + mcjPatterns },
)

fun List<ExtractPattern>.compile(): Map<String, List<ExtractPattern>> =
    MCFBuiltinPatterns + groupBy { it.command }.toMap()

suspend fun MCTWorkspace.extractFromDatapackRaw(
    mcfPatterns: ExtractPatternSet = MCFBuiltinPatterns,
    mcfDataPatterns: List<DataPointerPattern>? = BuiltinMCFunctionDataPatterns,
    mcjPatterns: List<DataPointerPattern>? = MCJsonBuiltinPatterns
): Flow<DatapackExtractionGroup> {
    if (mcjPatterns == null) logger.warning { "The filter was disabled, which causes export all string from the datapack" }
    logger.info { "Scanning datapacks in $datapackDir" }

    val extractors = listOf(
        MCFunctionExtractor(mcfPatterns, mcfDataPatterns),
        MCJsonExtractor(mcjPatterns),
    )

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

        }
        .flatMapMerge { (sfs, path) ->
            flow {
                sfs.useAsync { sfs ->
                    sfs.listRecursively(Path.ROOT)
                        .asFlow()
                        .mapNotNull { zpath ->
                            nullable { zpath to extractors.find { zpath.endsWith(it.targetExtension) }.bind() }
                        }
                        .flatMapMerge { (filePath, extractor) ->
                            either {
                                env.logger.debug {
                                    "Extracting $filePath via $extractor"
                                }
                                flowOf(extractor.extract(env, sfs, filePath, path))
                            }.getOrElse { error ->
                                env.logger.error { error.message }
                                emptyFlow()
                            }
                        }
                        .filter { it.extractions.isNotEmpty() }
                        .collect { emit(it) }
                }
            }
        }.flowOn(DispatcherOrIO)
}


internal interface Extractor {
    val targetExtension: String

    context(_: Raise<ExtractError>)
    suspend fun extract(
        env: Env,
        zfs: AsyncFileSystem,
        zpath: Path,
        path: Path
    ): DatapackExtractionGroup
}

internal fun Extractor(
    name: String,
    targetExtension: String,
    extract: suspend context(Raise<ExtractError>) (
        env: Env,
        zfs: AsyncFileSystem,
        zpath: Path,
        path: Path
    ) -> DatapackExtractionGroup
) = object : Extractor {
    override val targetExtension = targetExtension

    context(_: Raise<ExtractError>)
    override suspend fun extract(
        env: Env,
        zfs: AsyncFileSystem,
        zpath: Path,
        path: Path
    ): DatapackExtractionGroup = extract(env, zfs, zpath, path)

    override fun toString() = "Extractor($name)"
}