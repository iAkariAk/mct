package mct.model.patch

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mct.LoggerHolder
import mct.MCTPattern
import mct.command.extractTextFromCommands
import mct.dp.mcjson.backfillMCJson
import mct.dp.mcjson.extractTextFromMCJson
import mct.logger
import mct.model.DataPointerPatternKind
import mct.nbt.backfillSnbt
import mct.nbt.extractTextFromSnbt
import mct.pointer.DataPointer
import mct.serializer.IntRangeSerializable
import mct.util.StringIndices

context(_: LoggerHolder)
inline fun ExtractionContent.replace(
    noinline replace: (String) -> String?,
): ReplacementContent? = replace(
    replaceText = replace,
    replaceCommand = { it.map(replace) }
)

context(_: LoggerHolder)
fun ExtractionContent.replace(
    replaceText: (String) -> String?,
    replaceCommand: (List<String>) -> List<String?>,
): ReplacementContent? = when (this) {
    is ExtractionContent.Command -> replace(replaceCommand)
    is ExtractionContent.Text -> replace(replaceText)
    is ExtractionContent.Structure -> replaceX(replaceText, replaceCommand)
}

fun ExtractionContent.contentsWithFormat(): Sequence<Pair<FormatKind, String>> = when (this) {
    is ExtractionContent.Command -> locations.asSequence().map { it.format to it.unquoted() }
    is ExtractionContent.Structure -> contents.asSequence().flatMap { it.content.contentsWithFormat() }
    is ExtractionContent.Text -> sequenceOf(format to content)
}

fun ExtractionContent.contents(): Sequence<String> = when (this) {
    is ExtractionContent.Command -> locations.asSequence().map { it.unquoted() }
    is ExtractionContent.Structure -> contents.asSequence().flatMap { it.content.contents() }
    is ExtractionContent.Text -> sequenceOf(content)
}

interface PointedContent<T> {
    val pointer: DataPointer
    val content: T
}

interface IPointedExtractionContent {
    val pointer: DataPointer
    val format: FormatKind
    val extraction: ExtractionContent
}

@Serializable
data class PointedExtractionContent(
    override val pointer: DataPointer,
    override val content: ExtractionContent
) : PointedContent<ExtractionContent>, IPointedExtractionContent {
    override val format get() = content.format
    override val extraction get() = content
}


interface IPointedReplacementContent {
    val pointer: DataPointer
    val replacement: String
    val format: FormatKind
}

@Serializable
data class PointedReplacementContent(
    override val pointer: DataPointer,
    override val content: ReplacementContent
) : PointedContent<ReplacementContent>, IPointedReplacementContent {
    override val format get() = content.format
    override val replacement get() = content.replacement
}

inline fun PointedExtractionContent.replace(replace: (ExtractionContent) -> ReplacementContent) =
    PointedReplacementContent(pointer, replace(content))

@Serializable
sealed interface ExtractionContent {
    val format: FormatKind

    @Serializable
    @SerialName("text")
    data class Text(
        override val format: FormatKind = PlainStr,
        val content: String,
    ) : ExtractionContent {
        inline fun replace(replace: (String) -> String?): ReplacementContent.Text? =
            ReplacementContent.Text(format, replace(content) ?: return null)
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

    @Serializable
    @SerialName("structure")
    data class Structure(
        override val format: FormatKind = PlainStr,
        val raw: String,
        val contents: List<PointedExtractionContent>
    ) : ExtractionContent {
        context(_: LoggerHolder)
        fun replaceX(
            replaceText: (String) -> String?,
            replaceCommand: (List<String>) -> List<String?>,
        ): ReplacementContent.Structure? = if (contents.isEmpty()) null
        else {
            val replacements = contents.mapNotNull {
                it.replace { it.replace(replaceText, replaceCommand) ?: return@mapNotNull null }
            }.ifEmpty { return null }
            val replacement = when (format) {
                PlainStr -> raw
                SnbtStr, Nbt -> raw.backfillSnbt(replacements)
                JsonStr, JsonObj -> raw.backfillMCJson(replacements)
            }

            ReplacementContent.Structure(format, replacement)
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

    @Serializable
    @SerialName("structure")
    data class Structure(
        override val format: FormatKind,
        override val replacement: String
    ) : ReplacementContent


}


@Serializable
sealed class ContentKind {
    context(_: LoggerHolder)
    abstract fun parse(
        raw: String,
        format: FormatKind = PlainStr,
        pattern: MCTPattern = MCTPattern.Default
    ): ExtractionContent?

    @Serializable
    data object Text : ContentKind() {
        context(_: LoggerHolder)
        override fun parse(raw: String, format: FormatKind, pattern: MCTPattern): ExtractionContent =
            ExtractionContent.Text(format, raw)
    }

    @Serializable
    data object Command : ContentKind() {
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

    @Serializable
    data class Structure(val format: FormatKind, val patterns: DataPointerPatternKind) : ContentKind() {
        context(_: LoggerHolder)
        override fun parse(raw: String, format: FormatKind, pattern: MCTPattern): ExtractionContent? = runCatching {
            when (this.format) {
                PlainStr -> ExtractionContent.Text(PlainStr, raw)
                SnbtStr, Nbt -> {
                    val patterns = patterns.patternsFrom(pattern)
                    val contents = extractTextFromSnbt(raw, pattern, patterns).toList()
                    ExtractionContent.Structure(this.format, raw, contents)
                }

                JsonStr, JsonObj -> {
                    val patterns = patterns.patternsFrom(pattern)
                    val contents = extractTextFromMCJson(raw, pattern, patterns).toList()
                    ExtractionContent.Structure(this.format, raw, contents)
                }
            }
        }.getOrElse {
            logger.error { "When parsing ```$raw```, $this occurs ${it.message}" }
            null
        }
    }
}