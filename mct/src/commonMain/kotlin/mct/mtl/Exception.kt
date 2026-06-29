package mct.mtl


open class MTLParseException(msg: String, val position: IntRange) : Exception(msg)
class MTLEOFException(msg: String, position: IntRange) : MTLParseException(msg, position)

internal fun parseError(message: String, position: IntRange): Nothing = throw MTLParseException(message, position)
internal fun aheadEOF(position: IntRange): Nothing = throw MTLEOFException("Unexpected EOF ahead", position)

