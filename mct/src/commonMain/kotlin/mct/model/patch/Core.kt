package mct.model.patch

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mct.util.*


@Serializable
sealed interface ExtractionGroup {
    val extractions: List<Extraction>

    fun replace(replacements: List<Replacement>): ReplacementGroup
}

@Serializable
sealed interface Extraction

@Serializable
sealed interface ReplacementGroup {
    val replacements: List<Replacement>
}

@Serializable
sealed interface Replacement {
    val replacement: String
}

// Used for representing which form a data was stored
@Serializable
enum class FormatKind {
    @SerialName("plain_str")
    PlainStr, // command etc.

    @SerialName("snbt_str")
    SnbtStr,

    @SerialName("json_str")
    JsonStr, // includes plain text without quote

    @SerialName("json_obj")
    JsonObj, // refer to MCJson

    @SerialName("nbt_obj")
    Nbt // displayed as snbt
}

// Used to distinguish what type the extracted part is
@Serializable
enum class SnbtSyntaxKind {
    Compound,
    List,

    // String
    SingleQuoteString,
    DoubleQuoteString,
    LiteralString;
}


fun String.unquoted(syntax: SnbtSyntaxKind?) = when (syntax) {
    SnbtSyntaxKind.SingleQuoteString -> singleUnquoted()
    SnbtSyntaxKind.DoubleQuoteString -> doubleUnquoted()
    else -> this
}

fun String.quoted(syntax: SnbtSyntaxKind?) = when (syntax) {
    SnbtSyntaxKind.SingleQuoteString -> singleQuoted()
    SnbtSyntaxKind.DoubleQuoteString -> doubleQuoted()
    SnbtSyntaxKind.LiteralString -> doubleQuoted()
    else -> this
}

fun String.doubleQuotedIfString(syntax: SnbtSyntaxKind?) = when (syntax) {
    SnbtSyntaxKind.SingleQuoteString -> doubleQuoted()
    SnbtSyntaxKind.DoubleQuoteString -> doubleQuoted()
    SnbtSyntaxKind.LiteralString -> doubleQuoted()
    else -> this
}


fun FormatKind.isString(): Boolean =
    this == FormatKind.JsonStr || this == FormatKind.SnbtStr || this == FormatKind.PlainStr

fun FormatKind.isStructure(): Boolean = this == FormatKind.Nbt

fun FormatKind.validate(value: String): Boolean = when (this) {
    FormatKind.Nbt -> value.isSnbt()
    FormatKind.SnbtStr -> value.isSnbt()
    FormatKind.JsonStr -> value.isJson()
    FormatKind.JsonObj -> value.isJson()
    FormatKind.PlainStr -> true
}

inline fun Extraction.contents() = when (this) {
    is DatapackExtraction.MCFunction -> sequenceOf(unquoted())
    is DatapackExtraction.MCJson -> sequenceOf(content)
    is RegionExtraction -> when (nbt) {
        is NbtExtraction.Command -> nbt.locations.asSequence().map { it.unquoted() }
        is NbtExtraction.Text -> sequenceOf(nbt.content)
    }

    is DatapackExtraction.Nbt -> when (nbt) {
        is NbtExtraction.Command -> nbt.locations.asSequence().map { it.unquoted() }
        is NbtExtraction.Text -> sequenceOf(nbt.content)
    }
}