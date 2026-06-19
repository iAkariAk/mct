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
) = map { group ->
    when (group) {
        is DatapackExtractionGroup -> DatapackReplacementGroup(
            source = group.source, path = group.path, replacements = group.extractions.mapNotNull { extraction ->
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
            })

        is RegionExtractionGroup -> RegionReplacementGroup(
            dimension = group.dimension,
            kind = group.kind,
            coord = group.coord,
            replacements = group.extractions.mapNotNull { extraction ->
                extraction.substitute {
                    it.replace(nbtTextReplace, nbtCommandReplace) ?: return@mapNotNull null
                }
            }
        )
    }
}


inline fun NbtExtraction.replace(
    nbtTextReplace: (String) -> String?,
    nbtCommandReplace: (List<String>) -> List<String?>,
): NbtReplacement? = when (this) {
    is NbtExtraction.Command -> replace { nbtCommandReplace(it) }
    is NbtExtraction.Text -> replace { nbtTextReplace(it) ?: return null }
}