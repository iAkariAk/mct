@file:Suppress("FunctionName")

package mct.dp.mcjson

import mct.pointer.PatternSet
import mct.pointer.RegexPattern
import mct.pointer.RightPattern

val BuiltinPatterns = PatternSet {
    +RightPattern("#components>#custom_name")
    +RegexPattern("""#components>#lore>\d+$""")
}