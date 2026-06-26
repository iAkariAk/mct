package mct.command

import kotlinx.serialization.Serializable
import mct.model.patch.SnbtSyntaxKind
import mct.serializer.Regex2Serializable

@Serializable
data class CommandRegexPattern(
    val regex: Regex2Serializable,
    val groups: Map<Int, SnbtSyntaxKind?>, // 0 -> entire
)