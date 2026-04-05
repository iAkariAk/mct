package mct.dp

import arrow.core.getOrElse
import arrow.core.raise.Raise
import arrow.core.raise.context.either
import arrow.core.raise.nullable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.*
import mct.DatapackExtractionGroup
import mct.Env
import mct.MCTWorkspace
import mct.dp.mcfunction.ExtractPattern
import mct.dp.mcfunction.ExtractPatternSet
import mct.dp.mcfunction.MCFunctionExtractor
import mct.dp.mcjson.MCJsonExtractor
import mct.pointer.DataPointerPattern
import mct.util.io.*
import okio.FileSystem
import okio.Path
import mct.dp.mcfunction.BuiltinPatterns as MCFBuiltinPatterns
import mct.dp.mcjson.BuiltinPatterns as MCJsonBuiltinPatterns

fun MCTWorkspace.extractFromDatapack(
    mcfPatterns: List<ExtractPattern> = emptyList(),
    mcjPatterns: List<DataPointerPattern>? = emptyList()
) = extractFromDatapack(
    MCFBuiltinPatterns + mcfPatterns.groupBy { it.command }.toMap(),
    mcjPatterns?.let { MCJsonBuiltinPatterns + mcjPatterns },
)

private fun MCTWorkspace.extractFromDatapack(
    mcfPatterns: ExtractPatternSet = MCFBuiltinPatterns,
    mcjPatterns: List<DataPointerPattern>? = MCJsonBuiltinPatterns
): Flow<DatapackExtractionGroup> {
    if (mcjPatterns == null) logger.warning { "The filter MCJson was disabled, which causes export all string from the datapack" }

    val extractors = listOf(
        MCFunctionExtractor(mcfPatterns),
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
                    fs.openZipReadWrite(it)
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
        }.flowOn(Dispatchers.IO)
}


internal interface Extractor {
    val targetExtension: String

    context(_: Raise<ExtractError>)
    fun extract(
        env: Env,
        zfs: FileSystem,
        zpath: Path,
        path: Path
    ): DatapackExtractionGroup
}

internal fun Extractor(
    name: String,
    targetExtension: String,
    extract: context(Raise<ExtractError>) (
        env: Env,
        zfs: FileSystem,
        zpath: Path,
        path: Path
    ) -> DatapackExtractionGroup
) = object : Extractor {
    override val targetExtension = targetExtension

    context(_: Raise<ExtractError>)
    override fun extract(
        env: Env,
        zfs: FileSystem,
        zpath: Path,
        path: Path
    ): DatapackExtractionGroup = extract(env, zfs, zpath, path)

    override fun toString() = "Extractor($name)"
}