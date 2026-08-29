package mct.cext

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mct.MCTPattern
import mct.model.CommandPatternKind
import mct.model.CommandRegexPatternKind
import mct.model.DataPointerPatternKind
import mct.model.patch.NbtCompressionKind
import mct.util.toRegex2

@Serializable
data class CextPattern(
    @SerialName("opt_in")
    val optIn: List<CextBuiltinPattern> = emptyList(),
    val customs: List<CextPatternEntry>
) {
    operator fun plus(other: CextPattern) = CextPattern(
        optIn = optIn + other.optIn,
        customs = customs + other.customs
    )

    fun flatten(): List<CextPatternEntry> = optIn.flatMap(CextBuiltinPattern::patterns) + customs
}

@Serializable
sealed interface CextBuiltinPattern {
    val patterns: List<CextPatternEntry>
}

@Serializable
data class CextPatternEntry(
    val select: String, // regex
    val kind: CextFormatKind
) {
    val patternRegex by lazy { select.toRegex2() }
}

@Serializable
sealed interface CextFormatKind {
    fun attachTo(pattern: MCTPattern): MCTPattern

    @Serializable
    @SerialName("mcjson")
    data class MCJson(val patterns: DataPointerPatternKind = DataPointerPatternKind.InheritFrom.MCJson) :
        CextFormatKind {
        override fun attachTo(pattern: MCTPattern) = pattern.copy(
            mcjson = patterns.patternsFrom(pattern)
        )
    }

    @Serializable
    @SerialName("mcfunction")
    data class MCFunction(
        val command: CommandPatternKind = CommandPatternKind.Inherited,
        val commandData: DataPointerPatternKind = DataPointerPatternKind.InheritFrom.CommandData,
        val commandRegex: CommandRegexPatternKind = CommandRegexPatternKind.Inherited
    ) : CextFormatKind {
        override fun attachTo(pattern: MCTPattern) = pattern.copy(
            command = command.patternsFrom(pattern),
            commandData = commandData.patternsFrom(pattern),
            commandRegex = commandRegex.patternsFrom(pattern)
        )
    }

    @Serializable
    @SerialName("nbt")
    data class Nbt(
        val compression: NbtCompressionKind = None,
        val patterns: DataPointerPatternKind = DataPointerPatternKind.InheritFrom.Nbt
    ) : CextFormatKind {
        override fun attachTo(pattern: MCTPattern) = pattern.copy(
            nbt = patterns.patternsFrom(pattern)
        )
    }

    @Serializable
    @SerialName("snbt")
    data class Snbt(val patterns: DataPointerPatternKind = DataPointerPatternKind.InheritFrom.Nbt) : CextFormatKind {
        override fun attachTo(pattern: MCTPattern) = pattern.copy(
            nbt = patterns.patternsFrom(pattern)
        )
    }
}
