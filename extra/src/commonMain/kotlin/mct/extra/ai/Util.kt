package mct.extra.ai

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import mct.util.Regex2
import mct.util.codePointToString
import kotlin.jvm.JvmName

const val TOKEN_COUNT_THRESHOLD = 2 shl 10 // n k

internal expect fun calculateToken(str: String): Int

@JvmName($$"chunkedByToken$String")
internal fun Iterable<String>.chunkedByToken(tokenSizePerChunk: Int = TOKEN_COUNT_THRESHOLD): Sequence<MutableList<String>> =
    chunkedByTokenImpl(tokenSizePerChunk) { it }

@JvmName($$"chunkedByToken$IndexedValue$String")
internal fun Iterable<IndexedValue<String>>.chunkedByToken(tokenSizePerChunk: Int = TOKEN_COUNT_THRESHOLD): Sequence<MutableList<IndexedValue<String>>> =
    chunkedByTokenImpl(tokenSizePerChunk) { it.value }

private inline fun <T> Iterable<T>.chunkedByTokenImpl(
    tokenSizePerChunk: Int = TOKEN_COUNT_THRESHOLD,
    crossinline content: (T) -> String,
): Sequence<MutableList<T>> = sequence {
    val tmp = mutableListOf<T>()
    var tokenCount = 0
    for (source in this@chunkedByTokenImpl) {
        val approximateTokenCount = calculateToken(content(source))
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


internal suspend inline fun <T, E> Iterable<T>.forEachConcurrently(
    concurrency: Int,
    dispatcher: CoroutineDispatcher,
    noinline access: (E) -> Unit,
    crossinline block: suspend (T, access: suspend (E) -> Unit) -> Unit,
) = coroutineScope {
    val mutex = Mutex()
    require(concurrency > 0)
    if (concurrency == 1) {
        forEach { block(it, access) }
    } else {
        val semaphore = Semaphore(concurrency)
        forEach {
            launch(dispatcher) {
                semaphore.withPermit {
                    block(it) { e ->
                        mutex.withLock {
                            access(e)
                        }
                    }
                }
            }
        }
    }
}


private val HEX = HexFormat {
    upperCase = true
    number {
        minLength = 4
        removeLeadingZeros = true
    }
}

internal fun escapeEspecialUnicode(str: String): String = buildString {
    var i = 0
    while (i < str.length) {
        var high10: Int
        var low10: Int
        val codeUnit = str[i].code
        var nextCodeUnit: Int = -1
        val codePoint = when (codeUnit) {
            in 0xD800..0xDBFF -> {
                if (i + 1 >= str.length) codeUnit else {
                    nextCodeUnit = str[i + 1].code
                    if (nextCodeUnit !in 0xDC00..0xDFFF) codeUnit else {
                        i++
                        high10 = codeUnit and 0x3FF
                        low10 = nextCodeUnit and 0x3FF
                        0x10000 + ((high10 shl 10) or low10)
                    }
                }
            }

            else -> codeUnit
        }

        when (codePoint) {
            in 0x00..0x09, in 0x0B..0x0C, in 0x0E..0x1F, // ASIIC controller without CR and LF
//            in 0x7F..0x9F,
            in 0xE000..0xF8FF, // PUA in BMP
            in 0xF0000..0xFFFFD, // PUA-A in SP
            in 0x100000..0x10FFFD, // PUA-B in SP
            in 0x200B..0x200F, 0xFEFF, // zero-widths, LTR, TRL
            in 0xD800..0xDBFF, in 0xDC00..0xDFFF // surrogate pair
                -> {
                val codePointHex = codePoint.toHexString(HEX)
                append($$"$MCT_UNICODE_$$codePointHex$")
            }

            in 0x0000..0xFFFF // BMP
                -> append(codeUnit.toChar())

            else -> { // other SP
                check(nextCodeUnit != -1)
                append(codeUnit.toChar())
                append(nextCodeUnit.toChar())
            }

        }

        i++
    }
}

private val REGEX_UNESCAPE_ESPECIAL_UNICODE = Regex2($$"\\$MCT_UNICODE_([0-9a-zA-Z]{1,6})\\$")
internal fun unescapeEspecialUnicode(str: String): String = REGEX_UNESCAPE_ESPECIAL_UNICODE.replace(str) { result ->
    val codePointHex = result.groupValues[1]
    val codePoint = codePointHex.hexToInt(HEX)
    codePoint.codePointToString()
}