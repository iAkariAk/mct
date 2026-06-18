package mct

import mct.command.BuiltinMCFPatterns
import mct.command.BuiltinMCFunctionDataPatterns
import mct.command.ExtractPatternSet
import mct.pointer.DataPointerPattern
import mct.region.BuiltinRegionPatterns

// null is disabling the pattern
data class MCTPattern(
    val region: List<DataPointerPattern>? = BuiltinRegionPatterns,
    val mcjson: List<DataPointerPattern>? = null,
    val mcfunctionData: List<DataPointerPattern>? = BuiltinMCFunctionDataPatterns,
    val mcfunction: ExtractPatternSet = BuiltinMCFPatterns
) {
    companion object {
        val Default = MCTPattern()
    }
}
