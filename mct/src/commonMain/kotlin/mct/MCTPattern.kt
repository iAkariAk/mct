package mct

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mct.cext.CextPattern
import mct.command.*
import mct.dp.mcjson.BuiltinMCJsonPatterns
import mct.nbt.BuiltinNbtPatterns
import mct.pointer.DataPointerPattern

// null is disabling the pattern
@Serializable
data class MCTPattern(
    val nbt: List<DataPointerPattern>? = BuiltinNbtPatterns,
    val mcjson: List<DataPointerPattern>? = BuiltinMCJsonPatterns,
    val command: ExtractPatternSet = BuiltinCommandPatterns,
    @SerialName("command_data")
    val commandData: List<DataPointerPattern>? = BuiltinCommandDataPatterns,
    @SerialName("command_component")
    val commandComponent: ComponentPatterns? = BuiltinMinecraftComponentPatterns,
    @SerialName("command_regex")
    val commandRegex: List<CommandRegexPattern> = emptyList(),
    val cext: CextPattern? = null
) {
    companion object {
        val Default = MCTPattern()
    }
}
