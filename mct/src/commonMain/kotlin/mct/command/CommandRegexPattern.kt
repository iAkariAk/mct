package mct.command

import kotlinx.serialization.Serializable
import mct.model.patch.FormatKind
import mct.model.patch.SnbtSyntaxKind
import mct.serializer.Regex2Serializable

@Serializable
data class GroupInfo(
    val syntax: SnbtSyntaxKind? = null,
    val format: FormatKind? = null  // the [format] is the content inside the quotation if [syntax] is any quote type
) {
    companion object {
        val Default = GroupInfo()
    }
}

@Serializable
data class CommandRegexPattern(
    val regex: Regex2Serializable,
    val groups: Map<Int, GroupInfo?>, // 0 -> entire
)