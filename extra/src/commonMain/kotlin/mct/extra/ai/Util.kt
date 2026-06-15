package mct.extra.ai

const val TOKEN_COUNT_THRESHOLD = 1 shl 10 // n k

internal expect fun calculateToken(str: String): Int

internal fun Iterable<IndexedValue<String>>.chunkedByToken(tokenSizePerChunk: Int = TOKEN_COUNT_THRESHOLD): Sequence<MutableList<IndexedValue<String>>> {
    return sequence {
        val tmp = mutableListOf<IndexedValue<String>>()
        var tokenCount = 0
        for (source in this@chunkedByToken) {
            val approximateTokenCount = calculateToken(source.value)
            if (tokenCount > 0 && tokenCount + approximateTokenCount > tokenSizePerChunk) {
                yield(tmp.toMutableList())
                tmp.clear()
                tokenCount = 0
            }
            tmp += source
            tokenCount += approximateTokenCount
        }
        if (tmp.isNotEmpty()) {
            yield(tmp.toMutableList())
        }
    }
}