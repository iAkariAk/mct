package mct

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mct.pointer.DataPointer
import mct.region.anvil.Coord
import mct.region.anvil.model.ChunkDataKind
import mct.serializer.IntRangeSerializable

sealed interface ExtractionGroup<E : Extraction> {
    val extractions: List<E>
}

sealed interface Extraction {
    val content: String
}

sealed interface ReplacementGroup<E : Replacement> {
    val replacements: List<E>
}

sealed interface Replacement {
    val replacement: String
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
) : ExtractionGroup<DatapackExtraction>

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
) : ExtractionGroup<RegionExtraction>


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
    ) : DatapackExtraction

    /**
     * A text-based extraction from a file within a datapack.
     * @property indices The absolute character indices within the entire file.
     */
    @Serializable
    @SerialName("MCFunction")
    data class MCFunction(
        val indices: IntRangeSerializable,
        override val content: String,
    ) : DatapackExtraction

}

/**
 * An extraction from an NBT structure within a region file.
 * @property index The linear index of the chunk within the region (0-1023).
 * @property pointer The NBT path/pointer to the specific tag.
 * @property content The extracted NBT pointer represented as an SNBT string.
 */
@Serializable
@SerialName("Region")
data class RegionExtraction(
    val index: Int,
    val pointer: DataPointer,
    override val content: String,
) : Extraction


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
) : ReplacementGroup<DatapackReplacement>


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
) : ReplacementGroup<RegionReplacement>


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
    override val replacement: String,
) : Replacement
