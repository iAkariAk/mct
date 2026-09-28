package mct.model.patch

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mct.MCTPattern
import mct.kit.TranslationMapping
import mct.serializer.IntRangeSerializable
import mct.util.BytesIndices
import mct.util.StringIndices
import mct.serializer.PathSerializable as Path

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
    val preprocessing: PatchPreprocessing

    @Serializable
    @SerialName("deferred")
    data class Deferred(
        override val metadata: PatchMetadata? = null,
        override val validation: PatchValidation? = null,
        override val preprocessing: PatchPreprocessing,
        val pattern: MCTPattern,
        val mapping: TranslationMapping
    ) : Patch

    @Serializable
    @SerialName("immediate")
    data class Immediate(
        override val metadata: PatchMetadata? = null,
        override val validation: PatchValidation? = null,
        override val preprocessing: PatchPreprocessing,
        @SerialName("replacement_groups")
        val replacementGroups: ReplacementGroups
    ) : Patch {
        @Serializable
        data class ReplacementGroups(
            val region: List<RegionReplacementGroup>,
            val datapack: List<DatapackReplacementGroup>,
            val cext: List<CextReplacementGroup>
        )
    }
}

@Serializable
data class PatchPreprocessing(
    val ordered: List<PatchPreprocessingOperation>,
    val unordered: List<PatchPreprocessingOperation>
) {
    companion object {
        val None = PatchPreprocessing(emptyList(), emptyList())
    }
}

@Suppress("ArrayInDataClass")
@Serializable
sealed interface PatchPreprocessingOperation {
    val path: Path

    @Serializable
    @SerialName("add")
    data class AddFile(override val path: Path, val content: ByteArray) : PatchPreprocessingOperation

    @Serializable
    @SerialName("remove")
    data class RemoveFile(override val path: Path) : PatchPreprocessingOperation

    @Serializable
    @SerialName("patch")
    sealed interface PatchFile : PatchPreprocessingOperation

    @Serializable
    @SerialName("patch_text")
    data class PatchTextFile(
        override val path: Path,
        val patches: List<TextPatch>
    ) : PatchFile

    @Serializable
    @SerialName("patch_binary")
    data class PatchBinaryFile(
        override val path: Path,
        val patches: List<BinaryPatch>
    ) : PatchFile

    @Serializable
    data class TextPatch(override val indices: IntRangeSerializable, override val content: String) : StringIndices

    @Serializable
    data class BinaryPatch(override val indices: IntRangeSerializable, override val bytes: ByteArray) : BytesIndices

}

@Serializable
enum class PathKind {
    @SerialName("deferred")
    Deferred,

    @SerialName("immediate")
    Immediate,
}