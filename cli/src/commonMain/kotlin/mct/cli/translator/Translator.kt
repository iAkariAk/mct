package mct.cli.translator

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class TranslateResponse(val texts: List<String>, val terms: TermTable)

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
    val terms: TermTable

    suspend fun translate(sources: List<String>): List<String>
}