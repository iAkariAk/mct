package mct.model.patch

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mct.LoggerHolder
import mct.MCTPattern
import mct.command.extractTextFromCommands
import mct.serializer.IntRangeSerializable
import mct.util.StringIndices

inline fun ExtractionContent.replace(
    replace: (String) -> String?,
): ReplacementContent? = replace(
    replaceText = replace,
    replaceCommand = { it.map(replace) }
)

inline fun ExtractionContent.replace(
    replaceText: (String) -> String?,
    replaceCommand: (List<String>) -> List<String?>,
): ReplacementContent? = when (this) {
    is ExtractionContent.Command -> replace(replaceCommand)
    is ExtractionContent.Text -> replace(replaceText)
}

@Serializable
sealed interface ExtractionContent {
    val format: FormatKind

    @Serializable
    @SerialName("text")
    data class Text(
        override val format: FormatKind = PlainStr,
        val content: String,
    ) : ExtractionContent {
        inline fun replace(replace: (String) -> String?): ReplacementContent.Text? = ReplacementContent.Text(format, replace(content) ?: return null)
    }

    @Serializable
    @SerialName("command")
    data class Command(
        val raw: String,
        val locations: List<Location>, // must be ordered ascendingly based on indices
    ) : ExtractionContent {
        override val format: FormatKind = FormatKind.PlainStr

        @Serializable
        data class Location(
            override val indices: IntRangeSerializable,
            override val content: String,
            val syntax: SnbtSyntaxKind? = null,
            val format: FormatKind = PlainStr
        ) : StringIndices {
            inline fun unquoted() = content.unquoted(syntax)
        }

        inline fun replace(replace: (List<String>) -> List<String?>): ReplacementContent.Command {
            val replacements = replace(locations.map { it.unquoted() })
            require(locations.size == replacements.size) { "locations.size should equal replacements.size" }
            var lastLoc: Location? = null
            return ReplacementContent.Command(
                locations.asSequence()
                    .zip(replacements.asSequence())
                    .sortedByDescending { (loc, _) -> loc.indices.first }
                    .fold(StringBuilder(raw)) { acc, (loc, r) ->
                        require(lastLoc == null || lastLoc.indices.first > loc.indices.last) {
                            "Replacements cannot overlap with each other ($lastLoc and $loc)"
                        }
                        lastLoc = loc
                        val rr = r?.doubleQuotedIfString(loc.syntax)
                        acc.setRange(loc.indices.first, loc.indices.last + 1, rr ?: return@fold acc)
                    }.toString()
            )
        }
    }
}

@Serializable
sealed interface ReplacementContent {
    val format: FormatKind
    val replacement: String

    @Serializable
    @SerialName("text")
    data class Text(
        override val format: FormatKind,
        override val replacement: String,
    ) : ReplacementContent


    @Serializable
    @SerialName("command")
    data class Command(
        override val replacement: String,
    ) : ReplacementContent {
        override val format: FormatKind get() = FormatKind.PlainStr
    }
}

@Serializable
sealed interface ContentKind {
    context(_: LoggerHolder)
    fun parse(raw: String, format: FormatKind = PlainStr, pattern: MCTPattern = MCTPattern.Default): ExtractionContent?

    data object Text : ContentKind {
        context(_: LoggerHolder)
        override fun parse(raw: String, format: FormatKind, pattern: MCTPattern): ExtractionContent = ExtractionContent.Text(format, raw)
    }

    data object Command : ContentKind {
        context(_: LoggerHolder)
        override fun parse(raw: String, format: FormatKind, pattern: MCTPattern): ExtractionContent? {
            return ExtractionContent.Command(
                raw = raw,
                locations = extractTextFromCommands(
                    commandStr = raw,
                    patterns = pattern
                ).takeIf { it.isNotEmpty() }?.map {
                    ExtractionContent.Command.Location(it.indices, it.content, it.syntax, it.format)
                } ?: return null)
        }
    }

    data class Structure(val format: FormatKind) : ContentKind {
        context(_: LoggerHolder)
        override fun parse(raw: String, format: FormatKind, pattern: MCTPattern): ExtractionContent? = when (this.format) {
            PlainStr -> ExtractionContent.Text(PlainStr, raw)
            SnbtStr, Nbt -> TODO()
            JsonStr, JsonObj -> TODO()
        }
    }


}