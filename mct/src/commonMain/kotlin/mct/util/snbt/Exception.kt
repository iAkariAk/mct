package mct.util.snbt


class ParseException(msg: String) : Exception(msg)

internal fun error(message: String): Nothing = throw ParseException(message)

internal fun aheadEOF(): Nothing = error("Unexpected EOF ahead")

