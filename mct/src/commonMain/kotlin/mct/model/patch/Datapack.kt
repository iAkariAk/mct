package mct.model.patch

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mct.pointer.DataPointer
import mct.serializer.IntRangeSerializable

/**
 * Data extracted from a Minecraft Datapack (zip or folder).
 * @property source The file name or identifier of the original datapack source.
 * @property path The internal file path within the datapack where the content was found.
 */
@Serializable
@SerialName("datapack")
data class DatapackExtractionGroup(
    val source: String,
    val path: String,
    override val extractions: List<DatapackExtraction>,
) : ExtractionGroup {
    override fun replace(replacements: List<Replacement>) =
        @Suppress("UNCHECKED_CAST")
        DatapackReplacementGroup(
            source = source,
            path = path,
            replacements = replacements as List<DatapackReplacement>,
        )
}


@Serializable
@SerialName("datapack")
sealed interface DatapackExtraction : Extraction {
    /**
     * A text-based extraction from a file within a datapack.
     * @property pointer The JsonElement path/pointer to the specific tag.
     */
    @Serializable
    @SerialName("mcjson")
    data class MCJson(
        val pointer: DataPointer,
        val content: String,
        val kind: FormatKind
    ) : DatapackExtraction {
        inline fun replace(replacement: (String) -> String) = DatapackReplacement.MCJson(pointer, replacement(content), kind)
    }

    /**
     * A text-based extraction from a file within a datapack.
     * @property indices The absolute character indices within the entire file.
     */
    @Serializable
    @SerialName("mcfunction")
    data class MCFunction(
        val indices: IntRangeSerializable,
        val content: String,
        val syntax: SnbtSyntaxKind?,
    ) : DatapackExtraction {
        inline fun unquoted() = content.unquoted(syntax)

        inline fun replace(replacement: (String) -> String): DatapackReplacement.MCFunction {
            val r = replacement(content.unquoted(syntax))
            return DatapackReplacement.MCFunction(indices, r.doubleQuotedIfString(syntax), syntax)
        }
    }

    @Serializable
    @SerialName("nbt")
    data class Nbt(
        val nbt: NbtExtraction,
    ) : DatapackExtraction {
        inline fun replace(replace: (NbtExtraction) -> NbtReplacement) = DatapackReplacement.Nbt(replace(nbt))
    }
}

internal inline fun DatapackExtraction.replace(replacement: (String) -> String): DatapackReplacement = when (this) {
    is DatapackExtraction.MCFunction -> replace(replacement)
    is DatapackExtraction.MCJson -> replace(replacement)
    is DatapackExtraction.Nbt -> TODO()
}


/**
 * Replacements to be applied to a specific file in a datapack.
 * @property source The identifier of the target datapack.
 * @property path The internal file path to be modified.
 */
@Serializable
@SerialName("datapack")
data class DatapackReplacementGroup(
    val source: String,
    val path: String,
    override val replacements: List<DatapackReplacement>,
) : ReplacementGroup


@Serializable
@SerialName("datapack")
sealed interface DatapackReplacement : Replacement {
    /**
     * A text replacement for a datapack file.
     * @property indices The absolute character range in the file to be replaced.
     * @property replacement The new string content to insert.
     */
    @Serializable
    @SerialName("mcfunction")
    data class MCFunction(
        val indices: IntRangeSerializable,
        override val replacement: String,
        val syntax: SnbtSyntaxKind?,
    ) : DatapackReplacement

    /**
     * A text replacement for a datapack file.
     * @property pointer The JSONElement path/pointer to the specific tag.
     * @property replacement The new string content to insert.
     */
    @Serializable
    @SerialName("mcjson")
    data class MCJson(
        val pointer: DataPointer,
        override val replacement: String,
        val kind: FormatKind
    ) : DatapackReplacement

    @Serializable
    @SerialName("nbt")
    data class Nbt(
        val nbt: NbtReplacement,
    ) : DatapackReplacement {
        override val replacement get() = nbt.replacement
    }
}
