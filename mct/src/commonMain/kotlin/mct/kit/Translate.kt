package mct.kit

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import mct.*
import mct.serializer.MCTJson
import mct.serializer.Snbt
import mct.text.TextCompound


typealias TranslationMapping = Map<String, String>
typealias TranslationPool = Set<String>

private fun trySimply(text: String): String = runCatching {
    val tc = MCTJson.decodeFromString<TextCompound>(text)
    MCTJson.encodeToString(tc)
}.getOrElse {
    runCatching {
        val tc = Snbt.decodeFromString<TextCompound>(text)
        Snbt.encodeToString(tc)
    }.getOrElse {
        text
    }
}


fun List<ExtractionGroup>.exportIntoPool(simply: Boolean): TranslationPool =
    flatMapTo(mutableSetOf()) {
        it.extractions.flatMap { extraction ->
            val contents = extraction.contents()
            if (!simply) contents else contents.map(::trySimply)
        }
    }


inline fun List<ExtractionGroup>.replaceSimply(mapping: TranslationMapping): List<ReplacementGroup> =
    replaceSimply { mapping.entries.fold(it) { ace, (k, v) -> ace.replace(k, v) } }

inline fun List<ExtractionGroup>.replaceSimply(replace: (String) -> String?): List<ReplacementGroup> = replace(
    mcfReplace = replace,
    mcjReplace = replace,
    regionTextReplace = replace,
    regionCommandReplace = { it.map { replace(it) } },
)


fun List<ExtractionGroup>.replace(mapping: TranslationMapping) = replace(
    mcfReplace = { mapping[it] },
    mcjReplace = { mapping[it] },
    regionTextReplace = { mapping[it] },
    regionCommandReplace = { it.map { mapping[it] } }
)

inline fun List<ExtractionGroup>.replace(
    mcfReplace: (String) -> String?,
    mcjReplace: (String) -> String?,
    regionTextReplace: (String) -> String?,
    regionCommandReplace: (List<String>) -> List<String?>,
) =
    map {
        when (it) {
            is DatapackExtractionGroup -> DatapackReplacementGroup(
                source = it.source,
                path = it.path,
                replacements = it.extractions.mapNotNull { extraction ->
                    when (extraction) {
                        is DatapackExtraction.MCFunction -> extraction.replace {
                            mcfReplace(it) ?: return@mapNotNull null
                        }

                        is DatapackExtraction.MCJson -> extraction.replace { mcjReplace(it) ?: return@mapNotNull null }
                    }
                })

            is RegionExtractionGroup -> RegionReplacementGroup(
                dimension = it.dimension,
                kind = it.kind,
                coord = it.coord,
                replacements = it.extractions.mapNotNull { extraction ->
                    when (extraction) {
                        is RegionExtraction.Command -> extraction.replace { regionCommandReplace(it) }
                        is RegionExtraction.Text -> extraction.replace {
                            regionTextReplace(it) ?: return@mapNotNull null
                        }
                    }
                }
            )
        }
    }
