package mct.kit

import mct.*
import kotlin.jvm.JvmName


typealias TranslationMapping = Map<String, String>
typealias TranslationPool = List<String>


fun List<ExtractionGroup<*>>.exportIntoPool(): List<String> =
    flatMap { it.extractions.map { it.content } }.distinct()

fun List<ExtractionGroup<*>>.replace(mapping: TranslationMapping): List<ReplacementGroup<*>> =
    map {
        when (it) {
            is DatapackExtractionGroup -> DatapackReplacementGroup(
                source = it.source,
                path = it.path,
                replacements = it.extractions.mapNotNull {
                    when (it) {
                        is DatapackExtraction.MCFunction -> DatapackReplacement.MCFunction(
                            indices = it.indices,
                            replacement = mapping[it.content] ?: return@mapNotNull null
                        )

                        is DatapackExtraction.MCJson -> DatapackReplacement.MCJson(
                            pointer = it.pointer,
                            replacement = mapping[it.content] ?: return@mapNotNull null
                        )
                    }
                }
            )

            is RegionExtractionGroup -> RegionReplacementGroup(
                dimension = it.dimension,
                kind = it.kind,
                coord = it.coord,
                replacements = it.extractions.mapNotNull {
                    RegionReplacement(
                        index = it.index,
                        pointer = it.pointer,
                        replacement = mapping[it.content] ?: return@mapNotNull null
                    )
                }
            )
        }
    }

@JvmName($$"replaceSimply$DatapackExtractionGroup")
inline fun List<DatapackExtractionGroup>.replaceSimply(mapping: TranslationMapping): List<DatapackReplacementGroup> =
    replaceSimply { mapping.entries.fold(it) { ace, (k, v) -> ace.replace(k, v) } }


@JvmName($$"replaceSimply$DatapackExtractionGroup")
inline fun List<DatapackExtractionGroup>.replaceSimply(replace: (String) -> String?) = map {
    DatapackReplacementGroup(
        source = it.source,
        path = it.path,
        replacements = it.extractions.mapNotNull { extraction ->
            when (extraction) {
                is DatapackExtraction.MCFunction -> DatapackReplacement.MCFunction(
                    indices = extraction.indices,
                    replacement = replace(extraction.content) ?: return@mapNotNull null
                )

                is DatapackExtraction.MCJson -> DatapackReplacement.MCJson(
                    pointer = extraction.pointer,
                    replacement = replace(extraction.content) ?: return@mapNotNull null
                )
            }

        })
}

@JvmName($$"replaceSimply$RegionExtractionGroup")
inline fun List<RegionExtractionGroup>.replaceSimply(mapping: TranslationMapping): List<RegionReplacementGroup> =
    replaceSimply { mapping.entries.fold(it) { ace, (k, v) -> ace.replace(k, v) } }

@JvmName($$"replaceSimply$RegionExtractionGroup")
inline fun List<RegionExtractionGroup>.replaceSimply(replace: (String) -> String?) = map {
    RegionReplacementGroup(
        dimension = it.dimension,
        kind = it.kind,
        coord = it.coord,
        replacements = it.extractions.mapNotNull {
            RegionReplacement(
                index = it.index,
                pointer = it.pointer,
                replacement = replace(it.content) ?: return@mapNotNull null
            )
        }
    )
}