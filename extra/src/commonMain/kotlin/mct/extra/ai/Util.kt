package mct.extra.ai

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
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