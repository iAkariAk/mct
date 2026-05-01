package mct.util.translator

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mct.Env
import mct.ExtractionGroup
import mct.ReplacementGroup
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

interface Translator {
    val env: Env
    val terms: TermTable

    suspend fun translate(sources: List<String>): List<String>
}

suspend fun Translator.translate(extractionGroups: List<ExtractionGroup>): List<ReplacementGroup> {
    val allTexts = extractionGroups
        .flatMap { it.extractions }
        .map { it.content }
        .filter { it.isNotBlank() }
        .distinct()

    val mapping = allTexts.zip(translate(allTexts)).toMap()
    env.logger.sign<TranslateSign> { TranslateSign.Progress(1f) }

    return extractionGroups.replace(mapping)
}

