package mct.mtl

import mct.kit.TranslationMapping
import mct.util.Regex2

private val RAW_REGEX = Regex2("""^\s*\|(.*?)\|\s*==>\s*\|(.*?)\|\s*""")

// MTL MTC Translation Language
// MTLX MTC Translation Language extension
data class MTLX(
    val mtlMappings: MTLMappings,
    val rawMappings: TranslationMapping
) {
    companion object {
        const val SEPARATOR_MTL = "---mtl---"
        const val SEPARATOR_RAW = "---raw---"

        fun fromString(mtlx: String): MTLX {
            val mtlStart = mtlx.indexOf(SEPARATOR_MTL)
            val rawStart = mtlx.indexOf(SEPARATOR_RAW)
            require(mtlStart != -1) { "Please add $SEPARATOR_MTL" }
            require(rawStart != -1) { "Please add $SEPARATOR_RAW" }
            require(mtlStart < rawStart) { "mtl must be the front of raw" }
            val mtlStr = mtlx.substring(mtlStart + SEPARATOR_MTL.length+1, rawStart)
            val rawStr = mtlx.substring(rawStart + SEPARATOR_RAW.length + 1)
            val mtl = MTLPaser.parse(mtlStr).toList()
            val raw = rawStr.lines().mapNotNull(::parseRaw).toMap()
            return MTLX(mtl, raw)
        }
    }
}

private fun parseRaw(raw: String): Pair<String, String>? {
    val trim = raw.trim()
    if (trim.isEmpty() || trim.startsWith('#')) return null
    val mr = RAW_REGEX.matchEntire(raw)
    val (_, l, r) = mr?.groupValues ?: error("No mapping item")
    return l to r
}
