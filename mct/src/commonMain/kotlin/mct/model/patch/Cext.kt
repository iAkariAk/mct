package mct.model.patch

import kotlinx.serialization.Serializable
import mct.cext.CextFormatKind

/**
 *  @property path The path to the file relating to the workspace dir
 */
@Serializable
data class CextExtractionGroup(
    val path: String,
    val kind: CextFormatKind,
    override val extractions: List<Extraction>
) : ExtractionGroup {
    override fun replace(replacements: List<Replacement>): ReplacementGroup =
        CextReplacementGroup(path, kind, replacements)
}

/**
 *  @property path The path to the file relating to the workspace dir
 */
@Serializable
data class CextReplacementGroup(
    val path: String,
    val kind: CextFormatKind,
    override val replacements: List<Replacement>
) : ReplacementGroup


