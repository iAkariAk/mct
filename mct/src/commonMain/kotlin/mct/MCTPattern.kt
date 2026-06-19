package mct

import mct.command.BuiltinMCFPatterns
import mct.command.BuiltinMCFunctionDataPatterns
import mct.command.ExtractPatternSet
import mct.nbt.BuiltinNbtPatterns
import mct.pointer.DataPointerPattern

// null is disabling the pattern
data class MCTPattern(
    val region: List<DataPointerPattern>? = BuiltinNbtPatterns,
    val mcjson: List<DataPointerPattern>? = null,
    val mcfunctionData: List<DataPointerPattern>? = BuiltinMCFunctionDataPatterns,
    val mcfunction: ExtractPatternSet = BuiltinMCFPatterns
) {
    companion object {
        val Default = MCTPattern()
    }
}
