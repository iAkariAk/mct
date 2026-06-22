package mct.util.snbt


open class ParseException(msg: String) : Exception(msg)
class IllegalTokenException(msg: String) : ParseException(msg)
class IllegalNumberException(msg: String) : ParseException(msg)
class EOFException(msg: String) : ParseException(msg)

internal fun parseError(message: String): Nothing = throw ParseException(message)
internal fun illegalToken(message: String): Nothing = throw IllegalTokenException(message)
internal fun illegalNumber(message: String): Nothing = throw IllegalNumberException(message)
internal fun aheadEOF(): Nothing = throw EOFException("Unexpected EOF ahead")

