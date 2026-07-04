package mct.model.patch

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mct.pointer.DataPointer
import mct.serializer.IntRangeSerializable
import mct.util.StringIndices

/** [Command] represents it from command block; [Text] from TextCompound
 *
 *  * @property pointer The NBT path/pointer to the specific tag
 *  * @property kind via which kind of format the content was stored
 */
@Serializable
sealed interface NbtExtraction {
    val pointer: DataPointer
    val kind: FormatKind

    @Serializable
    @SerialName("text")
    data class Text(
        override val pointer: DataPointer,
        override val kind: FormatKind,
        val content: String,
    ) : NbtExtraction {
        inline fun replace(replacement: (String) -> String) =
            NbtReplacement.Text(pointer, kind, replacement(content))
    }

    @Serializable
    @SerialName("command")
    data class Command(
        override val pointer: DataPointer,
        val raw: String,
        val locations: List<Location>, // must be ordered ascendingly based on indices
    ) : NbtExtraction {
        override val kind: FormatKind = FormatKind.PlainStr

        @Serializable
        data class Location(
            override val indices: IntRangeSerializable,
            override val content: String,
            val syntax: SnbtSyntaxKind?,
        ) : StringIndices {
            inline fun unquoted() = content.unquoted(syntax)
        }

        inline fun replace(replace: (List<String>) -> List<String?>): NbtReplacement.Command {
            val replacements = replace(locations.map { it.unquoted() })
            require(locations.size == replacements.size) { "locations.size should equal replacements.size" }
            return NbtReplacement.Command(
                pointer,
                locations.asSequence()
                    .zip(replacements.asSequence())
                    .sortedByDescending { (loc, _) -> loc.indices.first }
                    .fold(StringBuilder(raw)) { acc, (loc, r) ->
                        val rr = r?.doubleQuotedIfString(loc.syntax)
                        acc.setRange(loc.indices.first, loc.indices.last + 1, rr ?: return@fold acc)
                    }.toString()
            )
        }
    }
}

/**
 * `Command` represents it from command block; `Text` from TextCompound
 *
 *  @property pointer The NBT path/pointer identifying the tag to replace
 *  @property kind via which kind of format the replacement was stored
 */
@Serializable
sealed interface NbtReplacement : Replacement {
    val pointer: DataPointer
    val kind: FormatKind

    @Serializable
    @SerialName("text")
    data class Text(
        override val pointer: DataPointer,
        override val kind: FormatKind,
        override val replacement: String,
    ) : NbtReplacement


    @Serializable
    @SerialName("command")
    data class Command(
        override val pointer: DataPointer,
        override val replacement: String,
    ) : NbtReplacement {
        override val kind: FormatKind get() = FormatKind.PlainStr
    }
}
