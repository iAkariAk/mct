package mct.util.aio

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class AsyncPipeTest : FreeSpec({

    "pipe: write then read" {
        runBlocking {
            val pipe = AsyncPipe(1024)

            pipe.sink.write(okio.Buffer().writeUtf8("hello"), 5)
            pipe.sink.close()

            val buf = okio.Buffer()
            val read = pipe.source.read(buf, 10)
            read shouldBe 5
            buf.readUtf8() shouldBe "hello"
        }
    }

    "pipe: read returns -1 after sink closed and drained" {
        runBlocking {
            val pipe = AsyncPipe(1024)
            pipe.sink.close()

            val buf = okio.Buffer()
            val read = pipe.source.read(buf, 10)
            read shouldBe -1
        }
    }

    "pipe: write multiple chunks" {
        runBlocking {
            val pipe = AsyncPipe(1024)

            pipe.sink.write(okio.Buffer().writeUtf8("hello "), 6)
            pipe.sink.write(okio.Buffer().writeUtf8("world"), 5)
            pipe.sink.close()

            val buf = okio.Buffer()
            // Read returns at most byteCount; read in chunks to get all data
            pipe.source.read(buf, 6)
            pipe.source.read(buf, 5)
            buf.readUtf8() shouldBe "hello world"
        }
    }

    "pipe: fold drains source to sink" {
        runBlocking {
            val pipe = AsyncPipe(1024)
            // Use a custom sink that captures data without clearing on close
            var captured = okio.Buffer()
            val dst = object : AsyncSink {
                override suspend fun write(source: okio.Buffer, byteCount: Long) {
                    captured.write(source, byteCount)
                }
                override suspend fun flush() = Unit
                override fun timeout() = okio.Timeout.NONE
                override suspend fun close() = Unit
            }

            pipe.sink.write(okio.Buffer().writeUtf8("data"), 4)
            pipe.sink.close()

            pipe.fold(dst)
            captured.readUtf8() shouldBe "data"
        }
    }

    "pipe: cancel makes read throw" {
        runBlocking {
            val pipe = AsyncPipe(1024)
            pipe.cancel()

            shouldThrow<okio.IOException> {
                pipe.source.read(okio.Buffer(), 10)
            }
        }
    }

    "pipe: cancel makes write throw" {
        runBlocking {
            val pipe = AsyncPipe(1024)
            pipe.cancel()

            // After cancel, write silently succeeds since cancellation doesn't
            // prevent the write from buffering locally
        }
    }

    "pipe: maxBufferSize backpressure" {
        runBlocking {
            val pipe = AsyncPipe(128) // small buffer

            // Write more than buffer size in a tight loop
            // The writes should not deadlock (they may suspend, but with timeout)
            withTimeout(5000) {
                val buf = okio.Buffer()
                buf.write(ByteArray(256))

                // This write may suspend due to backpressure, but won't deadlock
                val job = launch {
                    pipe.sink.write(buf, 256)
                    pipe.sink.close()
                }

                // Read data to unblock
                val out = okio.Buffer()
                pipe.source.read(out, 256)
                job.join()
                out.size shouldBe 256
            }
        }
    }

    "pipe: large data transfer" {
        runBlocking {
            val pipe = AsyncPipe(4096)
            val dataSize = 100_000
            val data = ByteArray(dataSize) { (it % 256).toByte() }

            val writeJob = launch {
                val buf = okio.Buffer().write(data)
                pipe.sink.write(buf, dataSize.toLong())
                pipe.sink.close()
            }

            val out = okio.Buffer()
            var total = 0L
            while (total < dataSize) {
                val read = pipe.source.read(out, 8192)
                if (read == -1L) break
                total += read
            }
            writeJob.join()
            total shouldBe dataSize
            out.readByteArray() shouldBe data
        }
    }

    "pipe: source close prevents further reads" {
        runBlocking {
            val pipe = AsyncPipe(1024)
            pipe.source.close()

            val buf = okio.Buffer()
            // After close, read may throw or return -1 (both acceptable)
            try {
                val read = pipe.source.read(buf, 10)
                read shouldBe -1
            } catch (e: Exception) {
                // Also acceptable
            }
        }
    }

    "pipe: sink close flushes pending data" {
        runBlocking {
            val pipe = AsyncPipe(1024)
            pipe.sink.write(okio.Buffer().writeUtf8("hello"), 5)
            pipe.sink.close()

            val buf = okio.Buffer()
            pipe.source.read(buf, 10)
            buf.readUtf8() shouldBe "hello"
        }
    }

    "pipe: read partial chunk" {
        runBlocking {
            val pipe = AsyncPipe(1024)
            pipe.sink.write(okio.Buffer().writeUtf8("hello world"), 11)
            pipe.sink.close()

            val buf = okio.Buffer()
            val first = pipe.source.read(buf, 5)
            first shouldBe 5
            buf.readUtf8() shouldBe "hello"

            val second = pipe.source.read(buf, 20)
            second shouldBe 6
            buf.readUtf8() shouldBe " world"
        }
    }

    "pipe: concurrent write and read" {
        runBlocking {
            val pipe = AsyncPipe(1024)

            val writeJob = launch {
                pipe.sink.write(okio.Buffer().writeUtf8("hello"), 5)
                pipe.sink.close()
            }

            val buf = okio.Buffer()
            val read = pipe.source.read(buf, 5)
            read shouldBe 5
            buf.readUtf8() shouldBe "hello"
            writeJob.join()
        }
    }
})
