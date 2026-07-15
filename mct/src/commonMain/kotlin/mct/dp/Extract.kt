package mct.dp

import arrow.core.getOrElse
import arrow.core.raise.Raise
import arrow.core.raise.context.either
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import mct.EnvHolder
import mct.MCTPattern
import mct.MCTWorkspace
import mct.command.BuiltinCommandPatterns
import mct.command.CommandExtractPattern
import mct.dp.mcfunction.MCFunctionExtractor
import mct.dp.mcjson.MCJsonExtractor
import mct.dp.nbt.NbtExtractor
import mct.logger
import mct.model.patch.DatapackExtraction
import mct.model.patch.DatapackExtractionGroup
import mct.util.IO
import mct.util.io.*
import okio.Path

fun List<CommandExtractPattern>.compile(hasBuiltin: Boolean = true): Map<String, List<CommandExtractPattern>> {
    val new = groupBy { it.command }.toMap()
    return if (hasBuiltin) BuiltinCommandPatterns + new else new
}

fun MCTWorkspace.extractFromDatapack(
    pattern: MCTPattern = MCTPattern.Default,
): Flow<DatapackExtractionGroup> {
    if (pattern.mcjson == null) logger.warning { "The filter was disabled, which causes export all string out of mcjson from the datapack" }
    logger.info { "Scanning datapacks in $datapackDir" }

    val extractors = buildMap {
        fun add(constructExtractor: (pattern: MCTPattern) -> Extractor) {
            val extractor = constructExtractor(pattern)
            put(extractor.targetExtension, extractor)
        }
        add(::MCFunctionExtractor)
        add(::MCJsonExtractor)
        add(::NbtExtractor)
    }

    if (!fs.exists(datapackDir)) {
        logger.error { "Datapacks directory doesn't exist" }
        return emptyFlow()
    }

    return fs.list(datapackDir)
        .asFlow()
        .mapNotNull {
            val metadata = fs.metadata(it)
            val walk = if (metadata.isDirectory) {
                fs.walkDirectory(it)
            } else if (it.endsWith(".zip")) {
                fs.walkZip(it)
            } else null
            if (walk == null) return@mapNotNull null
            it to walk
        }.flatMapMerge { (sourcePath, walk) ->
            flow {
                walk.read { "__MACOSX" !in it.path.segments && it.size != 0L }.use { reading ->
                    reading.mapNotNull { reading ->
                        reading to (extractors[reading.file.path.extension] ?: return@mapNotNull null)
                    }
                        .flatMap { (reading, extractor) ->
                            either {
                                env.logger.debug {
                                    "Extracting ${reading.file.path} via $extractor"
                                }
                                sequenceOf(extractor.extractAsGroup(sourcePath, reading))
                            }.getOrElse { error ->
                                env.logger.error { error.message }
                                emptySequence()
                            }
                        }
                        .filter { it.extractions.isNotEmpty() }
                        .forEach {
                            emit(it)
                        }
                }
            }
        }.flowOn(Dispatchers.IO)
}


internal interface Extractor {
    val targetExtension: String

    context(_: Raise<ExtractError>, _: EnvHolder)
    fun extract(
        sourcePath: Path,
        reading: StreamingFileReading,
    ): List<DatapackExtraction>
}

internal fun Extractor(
    name: String,
    targetExtension: String,
    extract: context(Raise<ExtractError>, EnvHolder) (
        sourcePath: Path,
        reading: StreamingFileReading,
    ) -> List<DatapackExtraction>,
) = object : Extractor {
    override val targetExtension = targetExtension

    context(_: Raise<ExtractError>, _: EnvHolder)
    override fun extract(
        sourcePath: Path,
        reading: StreamingFileReading,
    ): List<DatapackExtraction> = extract(sourcePath, reading)

    override fun toString() = "Extractor($name)"
}

context(_: Raise<ExtractError>, _: EnvHolder)
private fun Extractor.extractAsGroup(
    sourcePath: Path,
    reading: StreamingFileReading,
): DatapackExtractionGroup {
    val extractions = extract(sourcePath, reading).toList()
    logger.debug { "Extracted ${extractions.size} texts from ${reading.file.path}" }
    return DatapackExtractionGroup(
        source = sourcePath.name,
        path = reading.file.path.toString(),
        extractions
    )
}