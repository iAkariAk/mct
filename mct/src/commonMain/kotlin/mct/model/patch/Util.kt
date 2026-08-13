package mct.model.patch

import mct.kit.TranslationMapping

inline fun List<ExtractionGroup>.replaceSimply(replace: (String) -> String?): List<ReplacementGroup> = replace(
    mcfReplace = replace,
    mcjReplace = { it.replace(replace) },
    nbtReplace = { it.replace(replace) },
)

fun List<ExtractionGroup>.replace(mapping: TranslationMapping) = replace(
    mcfReplace = { mapping[it] },
    mcjReplace = { it.replace(mapping::get) },
    nbtReplace = { it.replace(mapping::get) },
)

inline fun Extraction.replaceSimply(replace: (String) -> String?): Replacement? = replace(
    mcfReplace = replace,
    mcjReplace = { it.replace(replace) },
    nbtReplace = { it.replace(replace) },
)

inline fun Extraction.replace(
    mcfReplace: (String) -> String?,
    mcjReplace: (ExtractionContent) -> ReplacementContent?,
    nbtReplace: (ExtractionContent) -> ReplacementContent?,
): Replacement? = when (this) {
    is DatapackExtraction.MCFunction -> replace { mcfReplace(it) ?: return null }
    is DatapackExtraction.MCJson -> replace { mcjReplace(it) ?: return null }
    is DatapackExtraction.Nbt -> replace {
        it.replace { nbtReplace(it) ?: return null }
    }
    is RegionExtraction -> substitute {
        it.replace {
            nbtReplace(it) ?: return null
        }
    }
}

inline fun List<ExtractionGroup>.replace(
    mcfReplace: (String) -> String?,
    mcjReplace: (ExtractionContent) -> ReplacementContent?,
    nbtReplace: (ExtractionContent) -> ReplacementContent?,
) = mapNotNull outer@{ group ->
    when (group) {
        is DatapackExtractionGroup -> {
            val replacements = group.extractions.mapNotNull { extraction ->
                when (extraction) {
                    is DatapackExtraction.MCFunction -> extraction.replace { mcfReplace(it) ?: return@mapNotNull null }
                    is DatapackExtraction.MCJson -> extraction.replace { mcjReplace(it) ?: return@mapNotNull null }
                    is DatapackExtraction.Nbt -> extraction.replace {
                        it.replace { nbtReplace(it) ?: return@mapNotNull null }
                    }
                }
            }
            DatapackReplacementGroup(group.source, group.path, replacements.ifEmpty { return@outer null })
        }

        is RegionExtractionGroup -> {
            val replacements = group.extractions.mapNotNull { extraction ->
                extraction.substitute {
                    it.replace {
                        nbtReplace(it) ?: return@mapNotNull null
                    }
                }
            }
            RegionReplacementGroup(group.dimension, group.kind, group.coord, replacements.ifEmpty { return@outer null })
        }
    }
}


