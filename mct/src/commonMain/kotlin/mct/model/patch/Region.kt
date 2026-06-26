@file:Suppress("OVERRIDE_BY_INLINE")

package mct.model.patch

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mct.region.anvil.Coord
import mct.region.anvil.model.ChunkDataKind

/**
 * Data extracted from Minecraft Region files (.mca).
 * @property dimension The ID of the dimension (e.g., "minecraft:overworld").
 * @property kind The specific NBT storage type (e.g., entities, poi, or chunks).
 * @property coord The X and Z region coordinates.
 */
@Serializable
@SerialName("region")
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


/**
 * An extraction from an NBT structure within a region file.
 * @property index The linear index of the chunk within the region (0-1023).
 */

@Serializable
@SerialName("region")
data class RegionExtraction(
    val index: Int,
    val nbt: NbtExtraction,
) : Extraction {
    inline fun substitute(replace: (NbtExtraction) -> NbtReplacement) = RegionReplacement(index, replace(nbt))
}


/**
 * Replacements to be applied to a specific region file.
 * @property dimension The target dimension ID.
 * @property kind The type of chunk pointer being modified.
 * @property coord The region coordinates.
 */
@Serializable
@SerialName("region")
data class RegionReplacementGroup(
    val dimension: String,
    val kind: ChunkDataKind,
    val coord: Coord,
    override val replacements: List<RegionReplacement>,
) : ReplacementGroup


/**
 * A wrapper of [NbtReplacement] used to region
 *
 * @property index The linear index of the chunk (0-1023).
 */
@Serializable
@SerialName("region")
data class RegionReplacement(
    val index: Int,
    val nbt: NbtReplacement,
) : Replacement {
    override val replacement: String
        get() = nbt.replacement
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