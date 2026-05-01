package mct.util.translator

internal const val TOKEN_COUNT_THRESHOLD = 100 shl 10 // 100k

internal expect fun calculateToken(str: String): Int

internal fun List<String>.chunkedByToken(tokenSizePerChunk: Int = TOKEN_COUNT_THRESHOLD): Sequence<MutableList<String>> {
    val sources = this
    var tokenCount = 0
    return sequence {
        val tmp = mutableListOf<String>()
        sources.forEachIndexed { index, source ->
            val approximateTokenCount = calculateToken(source)
            val isThresholdOver = tokenCount + approximateTokenCount >= tokenSizePerChunk
            if (isThresholdOver) {
                require(tmp.isNotEmpty()) { "The content size too large." }
                yield(tmp)
                tokenCount = 0
                tmp.clear()
            }
            tmp += source
            tokenCount += approximateTokenCount
        }
        if (tmp.isNotEmpty()) {
            yield(tmp)
        }
    }
}