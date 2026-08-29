package mct.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mct.MCTPattern
import mct.command.CommandExtractPattern
import mct.command.CommandRegexPattern
import mct.command.ExtractPatternSet
import mct.dp.compileWith
import mct.pointer.DataPointerPattern
import mct.util.unreachable

@Serializable
data class CommandRegexPatternKind(
    val patterns: List<CommandRegexPattern>?, @SerialName("inherit") val inherit: Boolean = true
) {
    companion object {
        val Inherited = CommandRegexPatternKind(null, true)
    }

    fun patternsFrom(pattern: MCTPattern): List<CommandRegexPattern> = when {
        patterns.isNullOrEmpty() && !inherit -> emptyList()
        patterns.isNullOrEmpty() && inherit -> pattern.commandRegex
        /*!patterns.isNullOrEmpty() && */inherit -> pattern.commandRegex + patterns!!
        /*!patterns.isNullOrEmpty() && */!inherit -> patterns!!
        else -> unreachable
    }
}

@Serializable
data class CommandPatternKind(
    val patterns: List<CommandExtractPattern>?, @SerialName("inherit") val inherit: Boolean = true
) {
    companion object {
        val Inherited = CommandPatternKind(null, true)
    }

    fun patternsFrom(pattern: MCTPattern): ExtractPatternSet {
        return when {
            patterns.isNullOrEmpty() && !inherit -> emptyMap()
            patterns.isNullOrEmpty() && inherit -> pattern.command
            /*!patterns.isNullOrEmpty() && */ inherit -> patterns!!.compileWith(pattern.command)
            /*!patterns.isNullOrEmpty() && */ !inherit -> patterns!!.compileWith()
            else -> unreachable
        }
    }
}


@Serializable
sealed interface DataPointerPatternKind {
    fun patternsFrom(pattern: MCTPattern): List<DataPointerPattern>?

    @Serializable
    @SerialName("inherit_from")
    enum class InheritFrom : DataPointerPatternKind {
        @SerialName("nbt")
        Nbt,

        @SerialName("mcjson")
        MCJson,

        @SerialName("command_data")
        CommandData;

        override fun patternsFrom(pattern: MCTPattern) = when (this) {
            Nbt -> pattern.nbt
            MCJson -> pattern.mcjson
            CommandData -> pattern.commandData
        }
    }

    @Serializable
    @SerialName("custom")
    data class Custom(
        val patterns: List<DataPointerPattern>?, @SerialName("inherit_from") val inheritFrom: InheritFrom? = null
    ) : DataPointerPatternKind {
        override fun patternsFrom(pattern: MCTPattern): List<DataPointerPattern>? {
            val inheritFrom = inheritFrom?.patternsFrom(pattern)
            return when {
                patterns != null && inheritFrom != null -> patterns + inheritFrom
                patterns != null && inheritFrom == null -> patterns
                patterns == null && inheritFrom != null -> inheritFrom
                patterns == null && inheritFrom == null -> null
                else -> unreachable
            }
        }
    }
}