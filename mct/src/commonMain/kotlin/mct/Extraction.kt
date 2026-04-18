package mct

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mct.pointer.DataPointer
import mct.region.anvil.Coord
import mct.region.anvil.model.ChunkDataKind
import mct.serializer.IntRangeSerializable

@Serializable
sealed interface ExtractionGroup {
    val extractions: List<Extraction>

    fun replace(replacements: List<Replacement>): ReplacementGroup
}

@Serializable
sealed interface Extraction {
    val content: String

    fun replace(replacement: String): Replacement
}

@Serializable
sealed interface ReplacementGroup {
    val replacements: List<Replacement>
}

@Serializable
sealed interface Replacement {
    val replacement: String
}

@Serializable
enum class FormatKind {
    @SerialName("json")
    Json, // includes plain text without quote

    @SerialName("snbt")
    Snbt
}

/**
 * Data extracted from a Minecraft Datapack (zip or folder).
 * @property source The file name or identifier of the original datapack source.
 * @property path The internal file path within the datapack where the content was found.
 */
@Serializable
@SerialName("Datapack")
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

/**
 * Data extracted from Minecraft Region files (.mca).
 * @property dimension The ID of the dimension (e.g., "minecraft:overworld").
 * @property kind The specific NBT storage type (e.g., entities, poi, or chunks).
 * @property coord The X and Z region coordinates.
 */
@Serializable
@SerialName("Region")
data class RegionExtractionGroup(
    val dimension: String,
    val kind: ChunkDataKind,
    val coord: Coord,
    override val extractions: List<RegionExtraction>,
) : ExtractionGroup {
    override fun replace(replacements: List<Replacement>) =
        @Suppress("UNCHECKED_CAST")
        RegionReplacementGroup(
            dimension = dimension,
            kind = kind,
            coord = coord,
            replacements = replacements as List<RegionReplacement>,
        )
}


@Serializable
@SerialName("Datapack")
sealed interface DatapackExtraction : Extraction {
    /**
     * A text-based extraction from a file within a datapack.
     * @property pointer The JsonElement path/pointer to the specific tag.
     */
    @Serializable
    @SerialName("MCJson")
    data class MCJson(
        val pointer: DataPointer,
        override val content: String,
    ) : DatapackExtraction {
        override fun replace(replacement: String) = DatapackReplacement.MCJson(pointer, replacement)
    }

    /**
     * A text-based extraction from a file within a datapack.
     * @property indices The absolute character indices within the entire file.
     */
    @Serializable
    @SerialName("MCFunction")
    data class MCFunction(
        val indices: IntRangeSerializable,
        override val content: String,
    ) : DatapackExtraction {
        override fun replace(replacement: String) = DatapackReplacement.MCFunction(indices, replacement)
    }

}

/**
 * An extraction from an NBT structure within a region file.
 * @property index The linear index of the chunk within the region (0-1023).
 * @property pointer The NBT path/pointer to the specific tag.
 * @property kind which kind format the content was stored via
 * @property content The extracted NBT pointer represented as an SNBT string if [kind] is snbt; otherwise as JSON.
 */

@Serializable
@SerialName("Region")
data class RegionExtraction(
    val index: Int,
    val pointer: DataPointer,
    val kind: FormatKind = FormatKind.Json,
    override val content: String,
) : Extraction {
    override fun replace(replacement: String) = RegionReplacement(index, pointer, kind, replacement)
}

/**
 * Replacements to be applied to a specific file in a datapack.
 * @property source The identifier of the target datapack.
 * @property path The internal file path to be modified.
 */
@Serializable
@SerialName("Datapack")
data class DatapackReplacementGroup(
    val source: String,
    val path: String,
    override val replacements: List<DatapackReplacement>,
) : ReplacementGroup


/**
 * Replacements to be applied to a specific region file.
 * @property dimension The target dimension ID.
 * @property kind The type of chunk pointer being modified.
 * @property coord The region coordinates.
 */
@Serializable
@SerialName("Region")
data class RegionReplacementGroup(
    val dimension: String,
    val kind: ChunkDataKind,
    val coord: Coord,
    override val replacements: List<RegionReplacement>,
) : ReplacementGroup


@Serializable
@SerialName("Datapack")
sealed interface DatapackReplacement : Replacement {
    /**
     * A text replacement for a datapack file.
     * @property indices The absolute character range in the file to be replaced.
     * @property replacement The new string content to insert.
     */
    @Serializable
    @SerialName("MCFunction")
    data class MCFunction(
        val indices: IntRangeSerializable,
        override val replacement: String,
    ) : DatapackReplacement

    /**
     * A text replacement for a datapack file.
     * @property pointer The JSONElement path/pointer to the specific tag.
     * @property replacement The new string content to insert.
     */
    @Serializable
    @SerialName("MCJson")
    data class MCJson(
        val pointer: DataPointer,
        override val replacement: String,
    ) : DatapackReplacement

}

/**
 * An NBT replacement for a region file.
 * @property index The linear index of the chunk (0-1023).
 * @property pointer The NBT path/pointer identifying the tag to replace.
 * @property replacement The new NBT pointer in SNBT format.
 */
@Serializable
@SerialName("Region")
data class RegionReplacement(
    val index: Int,
    val pointer: DataPointer,
    val kind: FormatKind = FormatKind.Json,
    override val replacement: String,
) : Replacement
