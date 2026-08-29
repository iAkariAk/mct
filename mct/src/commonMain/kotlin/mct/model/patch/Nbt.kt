package mct.model.patch

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mct.serializer.NbtGzip
import mct.serializer.NbtNone
import mct.serializer.NbtZlib

@Serializable
enum class NbtCompressionKind {
    @SerialName("None")
    None,

    @SerialName("gzip")
    Gzip,

    @SerialName("zlib")
    Zlib;


    val nbtSerializer
        get() = when (this) {
            None -> NbtNone
            Gzip -> NbtGzip
            Zlib -> NbtZlib
        }
}

@Serializable
data class NbtExtraction(
    val compressionKind: NbtCompressionKind = None,
    val content: PointedExtractionContent
) : Extraction {
    val pointer get() = content.pointer
    val format get() = content.format
    val extraction get() = content.extraction

    inline fun replace(replace: (ExtractionContent) -> ReplacementContent) =
        NbtReplacement(compressionKind, content.replace(replace))
}

@Serializable
data class NbtReplacement(
    val compressionKind: NbtCompressionKind,
    val content: PointedReplacementContent
) : Replacement, IPointedReplacementContent {
    override val pointer get() = content.pointer
    override val format get() = content.format
    override val replacement get() = content.replacement
}

@Serializable
data class SnbtExtraction(
    val content: PointedExtractionContent
) : Extraction {
    val pointer get() = content.pointer
    val format get() = content.format
    val extraction get() = content.extraction

    inline fun replace(replace: (ExtractionContent) -> ReplacementContent) =
        SnbtReplacement(content.replace(replace))
}

@Serializable
data class SnbtReplacement(
    val content: PointedReplacementContent
) : Replacement, IPointedReplacementContent {
    override val pointer get() = content.pointer
    override val format get() = content.format
    override val replacement get() = content.replacement
}