package mct

import mct.command.BuiltinCommandDataPatterns
import mct.command.BuiltinCommandPatterns
import mct.command.CommandRegexPattern
import mct.command.ExtractPatternSet
import mct.dp.mcjson.BuiltinMCJPatterns
import mct.nbt.BuiltinNbtPatterns
import mct.pointer.DataPointerPattern

// null is disabling the pattern
data class MCTPattern(
    val nbt: List<DataPointerPattern>? = BuiltinNbtPatterns,
    val mcjson: List<DataPointerPattern>? = BuiltinMCJPatterns,
    val command: ExtractPatternSet = BuiltinCommandPatterns,
    val commandData: List<DataPointerPattern>? = BuiltinCommandDataPatterns,
    val commandRegex: List<CommandRegexPattern> = emptyList(),
) {
    companion object {
        val Default = MCTPattern()
    }
}
