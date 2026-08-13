package mct.model.patch

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mct.command.MCCommandJsonRight
import mct.model.text.isTextComponentJson
import mct.model.text.isTextComponentSnbt
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
sealed interface Replacement

// Used for representing which form a data was stored
@Serializable
enum class FormatKind {
    @SerialName("plain_str")
    PlainStr, // command etc.

    @SerialName("snbt_str")
    SnbtStr,

    @SerialName("json_str")
    JsonStr, // includes plain text without quote, i.e. JsonLiteral

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
    SingleQuoteString -> singleUnquoted()
    DoubleQuoteString -> doubleUnquoted()
    else -> this
}

fun String.quoted(syntax: SnbtSyntaxKind?) = when (syntax) {
    SingleQuoteString -> singleQuoted()
    DoubleQuoteString -> doubleQuoted()
    LiteralString -> doubleQuoted()
    else -> this
}

fun String.doubleQuotedIfString(syntax: SnbtSyntaxKind?) = when (syntax) {
    SingleQuoteString -> doubleQuoted()
    DoubleQuoteString -> doubleQuoted()
    LiteralString -> doubleQuoted()
    else -> this
}

fun String.inferFormatKind(shouldTextComponent: Boolean = false, json: EitherJson = MCCommandJsonRight): FormatKind =
    when {
        if (shouldTextComponent) isTextComponentSnbt() else isSnbt() -> FormatKind.SnbtStr
        if (shouldTextComponent) isTextComponentJson(json) else isJson(json) -> FormatKind.JsonStr
        else -> FormatKind.PlainStr
    }

fun FormatKind.isString(): Boolean =
    this == FormatKind.JsonStr || this == FormatKind.SnbtStr || this == FormatKind.PlainStr

fun FormatKind.validate(value: String): Boolean = when (this) {
    Nbt, SnbtStr -> value.isTextComponentSnbt()
    JsonObj, JsonStr -> value.isTextComponentJson()
    PlainStr -> true
}

inline fun Extraction.contents(): Sequence<String> = when (this) {
    is DatapackExtraction.MCFunction -> sequenceOf(unquoted())
    else -> {
        val content = when (this) {
            is DatapackExtraction.MCJson -> content
            is DatapackExtraction.Nbt -> nbt.content
            is RegionExtraction -> nbt.content
        }
        when (content) {
            is Command -> content.locations.asSequence().map { it.unquoted() }
            is Text -> sequenceOf(content.content)
            // TODO
        }
    }
}