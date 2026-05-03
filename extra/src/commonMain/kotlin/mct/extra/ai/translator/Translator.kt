package mct.extra.ai.translator

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mct.*
import mct.kit.replace


typealias TermTable = Set<Term>

@Serializable
data class Term(val source: String, val target: String, val type: TermType)

@Serializable
enum class TermType {
    @SerialName("name")
    Name,

    @SerialName("term")
    Term
}

interface Translator : EnvHolder {
    val terms: TermTable

    suspend fun translate(kind: FormatKind, sources: List<String>): List<String>
}

suspend fun Translator.translate(groups: List<ExtractionGroup>): Map<String, String> {
    if (groups.isEmpty()) {
        logger.debug { "Skipping empty group" }
        return emptyMap()
    }
    val extractions = groups.flatMap { it.extractions }.groupBy {
        when (it) {
            is DatapackExtraction -> FormatKind.Json
            is RegionExtraction -> it.kind
        }
    }
    val mapping = extractions.flatMap { (kind, extractions) ->
        val sources = extractions.asSequence().mapNotNull { it.content.takeIf(String::isNotBlank) }.distinct().toList()
        val translated = translate(
            kind,
            sources
        )
        sources.zip(translated)
    }.toMap()
    logger.sign<TranslateSign> { TranslateSign.Progress(1f) }
    env.logger.info { "Built mapping with ${mapping.size} entries" }
    return mapping
}


suspend fun Translator.translateReplace(groups: List<ExtractionGroup>): List<ReplacementGroup> =
    groups.replace(translate(groups))
