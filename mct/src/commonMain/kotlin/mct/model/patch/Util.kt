package mct.model.patch

import mct.kit.TranslationMapping

inline fun List<ExtractionGroup>.replaceSimply(replace: (String) -> String?): List<ReplacementGroup> = replace(
    mcfReplace = replace,
    mcjReplace = replace,
    nbtTextReplace = replace,
    nbtCommandReplace = { it.map { replace(it) } },
)

fun List<ExtractionGroup>.replace(mapping: TranslationMapping) = replace(
    mcfReplace = { mapping[it] },
    mcjReplace = { mapping[it] },
    nbtTextReplace = { mapping[it] },
    nbtCommandReplace = { it.map { mapping[it] } })

inline fun List<ExtractionGroup>.replace(
    mcfReplace: (String) -> String?,
    mcjReplace: (String) -> String?,
    nbtTextReplace: (String) -> String?,
    nbtCommandReplace: (List<String>) -> List<String?>,
) = mapNotNull outer@{ group ->
    when (group) {
        is DatapackExtractionGroup -> {
            val replacements = group.extractions.mapNotNull { extraction ->
                when (extraction) {
                    is DatapackExtraction.MCFunction -> extraction.replace { mcfReplace(it) ?: return@mapNotNull null }
                    is DatapackExtraction.MCJson -> extraction.replace { mcjReplace(it) ?: return@mapNotNull null }
                    is DatapackExtraction.Nbt -> extraction.replace {
                        it.replace(
                            nbtTextReplace,
                            nbtCommandReplace
                        ) ?: return@mapNotNull null
                    }
                }
            }
            DatapackReplacementGroup(group.source, group.path, replacements.ifEmpty { return@outer null })
        }

        is RegionExtractionGroup -> {
            val replacements = group.extractions.mapNotNull { extraction ->
                extraction.substitute {
                    it.replace(nbtTextReplace, nbtCommandReplace) ?: return@mapNotNull null
                }
            }
            RegionReplacementGroup(group.dimension, group.kind, group.coord, replacements.ifEmpty { return@outer null })
        }
    }
}


inline fun NbtExtraction.replace(
    nbtTextReplace: (String) -> String?,
    nbtCommandReplace: (List<String>) -> List<String?>,
): NbtReplacement? = when (this) {
    is NbtExtraction.Command -> replace { nbtCommandReplace(it) }
    is NbtExtraction.Text -> replace { nbtTextReplace(it) ?: return null }
}