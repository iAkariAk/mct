package mct

import mct.cext.CextPattern
import mct.command.*
import mct.dp.mcjson.BuiltinMCJsonPatterns
import mct.nbt.BuiltinNbtPatterns
import mct.pointer.DataPointerPattern

// null is disabling the pattern
data class MCTPattern(
    val nbt: List<DataPointerPattern>? = BuiltinNbtPatterns,
    val mcjson: List<DataPointerPattern>? = BuiltinMCJsonPatterns,
    val command: ExtractPatternSet = BuiltinCommandPatterns,
    val commandData: List<DataPointerPattern>? = BuiltinCommandDataPatterns,
    val commandComponent: ComponentPatterns? = BuiltinMinecraftComponentPatterns,
    val commandRegex: List<CommandRegexPattern> = emptyList(),
    val cext: CextPattern? = null
) {
    companion object {
        val Default = MCTPattern()
    }
}
