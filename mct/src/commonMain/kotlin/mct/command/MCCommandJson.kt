package mct.command

import arrow.core.right
import mct.util.NonstandardJson

val MCCommandJson = NonstandardJson(
    allowTrailingComma = true,
    allowIllegalEscape = true,
    allowSingleQuote = false,
    isLenient = false,
)

val MCCommandJsonRight = MCCommandJson.right()
