package mct.model.patch

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mct.MCTPattern
import mct.kit.TranslationMapping

@Serializable
data class PatchMetadata(
    val name: String,
)

@Serializable
data class PatchValidation(
    @SerialName("hash_tree")
    val hashTree: Map<String, String>
)

@Serializable
enum class PatchValidationFailureStrategy {
    @SerialName("ignore")
    Ignore,

    @SerialName("waring")
    Warning,

    @SerialName("failure")
    Failure,
}

@Serializable
sealed interface Patch {
    val metadata: PatchMetadata?
    val validation: PatchValidation?

    @Serializable
    @SerialName("deferred")
    data class Deferred(
        override val metadata: PatchMetadata? = null,
        override val validation: PatchValidation? = null,
        val pattern: MCTPattern,
        val mapping: TranslationMapping
    ) : Patch

    @Serializable
    @SerialName("immediate")
    data class Immediate(
        override val metadata: PatchMetadata? = null,
        override val validation: PatchValidation? = null,
        @SerialName("replacement_groups")
        val replacementGroups: List<ReplacementGroup>
    ) : Patch
}

@Serializable
enum class PathKind {
    @SerialName("deferred")
    Deferred,

    @SerialName("immediate")
    Immediate,
}