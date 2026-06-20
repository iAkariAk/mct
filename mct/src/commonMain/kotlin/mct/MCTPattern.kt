package mct

import mct.command.BuiltinMCFPatterns
import mct.command.BuiltinMCFunctionDataPatterns
import mct.command.ExtractPatternSet
import mct.command.RegexPattern
import mct.nbt.BuiltinNbtPatterns
import mct.pointer.DataPointerPattern

// null is disabling the pattern
data class MCTPattern(
    val nbt: List<DataPointerPattern>? = BuiltinNbtPatterns,
    val mcjson: List<DataPointerPattern>? = null,
    val mcfunctionData: List<DataPointerPattern>? = BuiltinMCFunctionDataPatterns,
    val mcfunction: ExtractPatternSet = BuiltinMCFPatterns,
    val mcfunctionRegex: List<RegexPattern> = emptyList(),
) {
    companion object {
        val Default = MCTPattern()
    }
}
