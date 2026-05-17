package mct.util.aio

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import okio.Path.Companion.toPath

class AsyncForwardingTest : FreeSpec({

    "AsyncForwardingSource delegates read" - {
        val inner = AsyncBuffer()
        inner.writeUtf8("test data")

        var calls = 0
        val forwarding = object : AsyncForwardingSource(inner) {
            override suspend fun read(sink: okio.Buffer, byteCount: Long): Long {
                calls++
                return super.read(sink, byteCount)
            }
        }

        val buf = okio.Buffer()
        forwarding.read(buf, 4)
        buf.readUtf8() shouldBe "test"
        calls shouldBe 1
    }

    "AsyncForwardingSource delegates close" - {
        var closed = false
        val inner = object : AsyncSource {
            override suspend fun read(sink: okio.Buffer, byteCount: Long) = -1L
            override fun timeout() = okio.Timeout.NONE
            override suspend fun close() {
                closed = true
            }
        }
        val forwarding = object : AsyncForwardingSource(inner) {}
        forwarding.close()
        closed shouldBe true
    }

    "AsyncForwardingSource delegates timeout" - {
        val inner = AsyncBuffer()
        val forwarding = object : AsyncForwardingSource(inner) {}
        forwarding.timeout() shouldBe inner.timeout()
    }

    "AsyncForwardingSink delegates write" - {
        val inner = AsyncBuffer()
        var calls = 0
        val forwarding = object : AsyncForwardingSink(inner) {
            override suspend fun write(source: okio.Buffer, byteCount: Long) {
                calls++
                super.write(source, byteCount)
            }
        }

        forwarding.write(okio.Buffer().writeUtf8("test"), 4)
        calls shouldBe 1
        inner.readUtf8() shouldBe "test"
    }

    "AsyncForwardingSink delegates flush" - {
        var flushed = false
        val inner = object : AsyncSink {
            override suspend fun write(source: okio.Buffer, byteCount: Long) = Unit
            override suspend fun flush() {
                flushed = true
            }

            override fun timeout() = okio.Timeout.NONE
            override suspend fun close() = Unit
        }
        val forwarding = object : AsyncForwardingSink(inner) {}
        forwarding.flush()
        flushed shouldBe true
    }

    "AsyncForwardingFileSystem delegates all calls" - {
        val inner = createTestFileSystem()
        val forwarding = object : AsyncForwardingFileSystem(inner) {}

        forwarding.exists("/".toPath()) shouldBe inner.exists("/".toPath())
    }

    "AsyncForwardingFileSystem can override specific calls" - {
        val inner = createTestFileSystem()
        var intercepted = false
        val forwarding = object : AsyncForwardingFileSystem(inner) {
            override suspend fun delete(path: okio.Path, mustExist: Boolean) {
                intercepted = true
                super.delete(path, mustExist)
            }
        }

        forwarding.exists("/".toPath()) // not intercepted
        intercepted shouldBe false
    }
})

private fun createTestFileSystem(): AsyncFileSystem {
    val fake = okio.fakefilesystem.FakeFileSystem()
    return fake.zio()
}
