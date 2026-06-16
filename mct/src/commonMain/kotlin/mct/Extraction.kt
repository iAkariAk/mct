@file:Suppress("OVERRIDE_BY_INLINE")

package mct

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mct.pointer.DataPointer
import mct.region.anvil.Coord
import mct.region.anvil.model.ChunkDataKind
import mct.serializer.IntRangeSerializable
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
sealed interface Replacement {
    val replacement: String
}

// Used for representing which form a data was stored
@Serializable
enum class FormatKind {
    @SerialName("plain_str")
    PlainStr, // command etc.

    @SerialName("snbt_str")
    SnbtStr,

    @SerialName("json_str")
    JsonStr, // includes plain text without quote

    @SerialName("nbt_binary")
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
    SnbtSyntaxKind.SingleQuoteString -> singleUnquoted()
    SnbtSyntaxKind.DoubleQuoteString -> doubleUnquoted()
    else -> this
}

fun String.quoted(syntax: SnbtSyntaxKind?) = when (syntax) {
    SnbtSyntaxKind.SingleQuoteString -> singleQuoted()
    SnbtSyntaxKind.DoubleQuoteString -> doubleQuoted()
    SnbtSyntaxKind.LiteralString -> doubleQuoted()
    else -> this
}


fun FormatKind.isString(): Boolean = this == FormatKind.JsonStr || this == FormatKind.SnbtStr || this == FormatKind.PlainStr
fun FormatKind.isBinary(): Boolean = this == FormatKind.Nbt

fun FormatKind.validate(value: String): Boolean = when (this) {
    FormatKind.Nbt -> value.isSnbt()
    FormatKind.SnbtStr -> value.isSnbt()
    FormatKind.JsonStr -> value.isJson()
    FormatKind.PlainStr -> true
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
        val content: String,
    ) : DatapackExtraction {
        inline fun replace(replacement: (String) -> String) = DatapackReplacement.MCJson(pointer, replacement(content))
    }

    /**
     * A text-based extraction from a file within a datapack.
     * @property indices The absolute character indices within the entire file.
     */
    @Serializable
    @SerialName("MCFunction")
    data class MCFunction(
        val indices: IntRangeSerializable,
        val content: String,
        val syntax: SnbtSyntaxKind?,
    ) : DatapackExtraction {
        inline fun unquoted() = content.unquoted(syntax)

        inline fun replace(replacement: (String) -> String): DatapackReplacement.MCFunction {
            val r = replacement(content.unquoted(syntax))
            return DatapackReplacement.MCFunction(indices, if (syntax == null) r else r.doubleQuoted(), syntax)
        }
    }
}

internal inline fun DatapackExtraction.replace(replacement: (String) -> String): DatapackReplacement = when (this) {
    is DatapackExtraction.MCFunction -> replace(replacement)
    is DatapackExtraction.MCJson -> replace(replacement)
}

/**
 * An extraction from an NBT structure within a region file.
 * @property index The linear index of the chunk within the region (0-1023).
 * @property pointer The NBT path/pointer to the specific tag.
 * @property kind which kind format the content was stored via
 */

@Serializable
@SerialName("Region")
sealed interface RegionExtraction : Extraction {
    val index: Int
    val pointer: DataPointer
    val kind: FormatKind


    @Serializable
    @SerialName("Text")
    data class Text(
        override val index: Int,
        override val pointer: DataPointer,
        override val kind: FormatKind = FormatKind.PlainStr,
        val content: String,
    ) : RegionExtraction {
        inline fun replace(replacement: (String) -> String) =
            RegionReplacement.Text(index, pointer, kind, replacement(content))
    }

    @Serializable
    @SerialName("Command")
    data class Command(
        override val index: Int,
        override val pointer: DataPointer,
        val raw: String,
        val locations: List<Location>, // must be ordered ascendingly based on indices
    ) : RegionExtraction {
        override val kind: FormatKind = FormatKind.PlainStr

        @Serializable
        data class Location(
            override val indices: IntRangeSerializable,
            override val content: String,
            val syntax: SnbtSyntaxKind?,
        ) : StringIndices {
            inline fun unquoted() = content.unquoted(syntax)
        }

        inline fun replace(replace: (List<String>) -> List<String?>): RegionReplacement.Command {
            val replacements = replace(locations.map { it.unquoted() })
            return RegionReplacement.Command(
                index,
                pointer,
                locations.asSequence()
                    .zip(replacements.asSequence())
                    .sortedByDescending { (loc, _) -> loc.indices.first }
                    .fold(StringBuilder(raw)) { acc, (loc, r) ->
                        val rr = if (loc.syntax == null) r else r?.doubleQuoted()
                        acc.setRange(loc.indices.first, loc.indices.last + 1, rr ?: return@fold acc)
                    }.toString()
            )
        }
    }

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
        val syntax: SnbtSyntaxKind?,
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
 * `Command` represents it from command block; `Text` from TextCompound
 *
 * @property index The linear index of the chunk (0-1023).
 * @property pointer The NBT path/pointer identifying the tag to replace.
 * @property kind which kind format the replacement was stored via
 */
@Serializable
@SerialName("Region")
sealed interface RegionReplacement : Replacement {
    val index: Int
    val pointer: DataPointer
    val kind: FormatKind

    @Serializable
    @SerialName("Text")
    data class Text(
        override val index: Int,
        override val pointer: DataPointer,
        override val kind: FormatKind = FormatKind.PlainStr,
        override val replacement: String,
    ) : RegionReplacement

    @Serializable
    @SerialName("Command")
    data class Command(
        override val index: Int,
        override val pointer: DataPointer,
        override val replacement: String,
    ) : RegionReplacement {
        override val kind: FormatKind get() = FormatKind.PlainStr
    }
}


inline fun Extraction.contents() = when (this) {
    is DatapackExtraction.MCFunction -> sequenceOf(unquoted())
    is DatapackExtraction.MCJson -> sequenceOf(content)
    is RegionExtraction.Command -> locations.asSequence().map { it.unquoted() }
    is RegionExtraction.Text -> sequenceOf(content)
}