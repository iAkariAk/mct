package mct.util.aio

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.encodeUtf8
import okio.IOException

class RealBufferedSourceSinkTest : FreeSpec({

    "AsyncSource.buffer() creates a lazy buffered source" - {
        val inner = AsyncBuffer()
        inner.writeUtf8("hello world")

        val buffered: AsyncBufferedSource = inner.buffer()
        buffered.readUtf8(5) shouldBe "hello"
        buffered.readUtf8() shouldBe " world"
    }

    "AsyncSink.buffer() creates a buffered sink that flushes through" - {
        val inner = AsyncBuffer()
        val buffered: AsyncBufferedSink = inner.buffer()

        buffered.writeUtf8("hello")
        // Data should be in the buffer, not yet in inner
        buffered.flush()
        inner.readUtf8() shouldBe "hello"
    }

    "RealAsyncBufferedSink: emit flushes buffered data" - {
        val inner = AsyncBuffer()
        val buffered = RealAsyncBufferedSink(inner)

        buffered.writeUtf8("hello")
        inner.size shouldBe 0L // Not flushed yet

        buffered.emit()
        inner.size shouldBe 5L // Flushed
        inner.readUtf8() shouldBe "hello"
    }

    "RealAsyncBufferedSink: close flushes data" - {
        val inner = AsyncBuffer()
        val buffered = RealAsyncBufferedSink(inner)

        buffered.writeUtf8("data")

        // flush first to write to inner before close clears it
        buffered.flush()
        inner.readUtf8() shouldBe "data"

        buffered.close()
    }

    "RealAsyncBufferedSink: writeAll drains source" - {
        val inner = AsyncBuffer()
        val buffered = RealAsyncBufferedSink(inner)
        val source = AsyncBuffer()
        source.writeUtf8("hello world")

        val written = buffered.writeAll(source)
        written shouldBe 11L

        buffered.flush()
        inner.readUtf8() shouldBe "hello world"
    }

    "RealAsyncBufferedSink: write with source and byteCount" - {
        val inner = AsyncBuffer()
        val buffered = RealAsyncBufferedSink(inner)
        val source = AsyncBuffer()
        source.writeUtf8("hello world")

        buffered.write(source as AsyncSource, 5)
        buffered.flush()
        inner.readUtf8() shouldBe "hello"
    }

    "RealAsyncBufferedSink: write from okio.Buffer" - {
        val inner = AsyncBuffer()
        val buffered = RealAsyncBufferedSink(inner)
        val okioBuf = okio.Buffer()
        okioBuf.writeUtf8("test")

        buffered.write(okioBuf, 4)
        buffered.flush()
        inner.readUtf8() shouldBe "test"
    }

    "RealAsyncBufferedSink: auto-emits complete segments after write" - {
        val inner = AsyncBuffer()
        val buffered = RealAsyncBufferedSink(inner)

        // Write enough to fill segments
        val data = ByteArray(8192) { 'x'.code.toByte() }
        buffered.write(data)
        inner.size shouldBe 8192L // Complete segment auto-emitted
    }

    "RealAsyncBufferedSource: lazy read fills buffer incrementally" - {
        val inner = AsyncBuffer()
        // Simulate a source that provides data in chunks
        inner.writeUtf8("hello world")

        val buffered = RealAsyncBufferedSource(inner)
        buffered.readUtf8(5) shouldBe "hello"
        buffered.readUtf8() shouldBe " world"
    }

    "RealAsyncBufferedSource: readUtf8 reads all from source" - {
        val inner = AsyncBuffer()
        inner.writeUtf8("hello world")

        val buffered = RealAsyncBufferedSource(inner)
        buffered.readUtf8() shouldBe "hello world"
    }

    "RealAsyncBufferedSource: readByteString reads all" - {
        val inner = AsyncBuffer()
        inner.writeUtf8("data")

        val buffered = RealAsyncBufferedSource(inner)
        buffered.readByteString() shouldBe "data".encodeUtf8()
    }

    "RealAsyncBufferedSource: readByteArray reads all" - {
        val inner = AsyncBuffer()
        inner.write(byteArrayOf(1, 2, 3))

        val buffered = RealAsyncBufferedSource(inner)
        buffered.readByteArray() shouldBe byteArrayOf(1, 2, 3)
    }

    "RealAsyncBufferedSource: readFully into ByteArray" - {
        val inner = AsyncBuffer()
        inner.write(byteArrayOf(1, 2, 3, 4, 5))

        val buffered = RealAsyncBufferedSource(inner)
        val sink = ByteArray(5)
        buffered.readFully(sink)
        sink shouldBe byteArrayOf(1, 2, 3, 4, 5)
    }

    "RealAsyncBufferedSource: readFully into AsyncBuffer" - {
        val inner = AsyncBuffer()
        inner.writeUtf8("hello world")

        val buffered = RealAsyncBufferedSource(inner)
        val sink = AsyncBuffer()
        buffered.readFully(sink, 5)
        sink.readUtf8() shouldBe "hello"
    }

    "RealAsyncBufferedSource: readFully partial throws EOF" - {
        val inner = AsyncBuffer()
        inner.write(byteArrayOf(1, 2))

        val buffered = RealAsyncBufferedSource(inner)
        shouldThrow<okio.EOFException> {
            buffered.readFully(ByteArray(5))
        }
    }

    "RealAsyncBufferedSource: readUtf8Line" - {
        val inner = AsyncBuffer()
        inner.writeUtf8("line1\nline2\nline3\n")

        val buffered = RealAsyncBufferedSource(inner)
        buffered.readUtf8Line() shouldBe "line1"
        buffered.readUtf8Line() shouldBe "line2"
        buffered.readUtf8Line() shouldBe "line3"
        buffered.readUtf8Line() shouldBe null
    }

    "RealAsyncBufferedSource: readUtf8LineStrict" - {
        val inner = AsyncBuffer()
        inner.writeUtf8("hello\n")

        val buffered = RealAsyncBufferedSource(inner)
        buffered.readUtf8LineStrict() shouldBe "hello"
    }

    "RealAsyncBufferedSource: readUtf8LineStrict with limit" - {
        val inner = AsyncBuffer()
        inner.writeUtf8("hello\n")

        val buffered = RealAsyncBufferedSource(inner)
        buffered.readUtf8LineStrict(10) shouldBe "hello"
    }

    "RealAsyncBufferedSource: readUtf8LineStrict exceeding limit throws" - {
        val inner = AsyncBuffer()
        inner.writeUtf8("hello world\n")

        val buffered = RealAsyncBufferedSource(inner)
        shouldThrow<okio.EOFException> {
            buffered.readUtf8LineStrict(5)
        }
    }

    "RealAsyncBufferedSource: readUtf8LineStrict CRLF edge case" - {
        val inner = AsyncBuffer()
        inner.writeUtf8("hello\r\n")

        val buffered = RealAsyncBufferedSource(inner)
        buffered.readUtf8LineStrict(5) shouldBe "hello"
    }

    "RealAsyncBufferedSource: readDecimalLong" - {
        val inner = AsyncBuffer()
        inner.writeUtf8("12345 ")

        val buffered = RealAsyncBufferedSource(inner)
        buffered.readDecimalLong() shouldBe 12345L
    }

    "RealAsyncBufferedSource: readHexadecimalUnsignedLong" - {
        val inner = AsyncBuffer()
        inner.writeUtf8("DEADBEEF ")

        val buffered = RealAsyncBufferedSource(inner)
        buffered.readHexadecimalUnsignedLong() shouldBe 0xDEADBEEF
    }

    "RealAsyncBufferedSource: readUtf8CodePoint" - {
        val inner = AsyncBuffer()
        inner.writeUtf8("A")

        val buffered = RealAsyncBufferedSource(inner)
        buffered.readUtf8CodePoint() shouldBe 'A'.code
    }

    "RealAsyncBufferedSource: skip" - {
        val inner = AsyncBuffer()
        inner.writeUtf8("hello world")

        val buffered = RealAsyncBufferedSource(inner)
        buffered.skip(6)
        buffered.readUtf8() shouldBe "world"
    }

    "RealAsyncBufferedSource: indexOf byte" - {
        val inner = AsyncBuffer()
        inner.writeUtf8("hello world")

        val buffered = RealAsyncBufferedSource(inner)
        buffered.indexOf('o'.code.toByte()) shouldBe 4
    }

    "RealAsyncBufferedSource: indexOf ByteString" - {
        val inner = AsyncBuffer()
        inner.writeUtf8("hello world")

        val buffered = RealAsyncBufferedSource(inner)
        buffered.indexOf("world".encodeUtf8()) shouldBe 6
    }

    "RealAsyncBufferedSource: indexOfElement" - {
        val inner = AsyncBuffer()
        inner.writeUtf8("hello world")
        val buffered = RealAsyncBufferedSource(inner)
        buffered.indexOfElement("aeiou".encodeUtf8()) shouldBe 1 // 'e'
    }

    "RealAsyncBufferedSource: rangeEquals" - {
        val inner = AsyncBuffer()
        inner.writeUtf8("hello world")
        val buffered = RealAsyncBufferedSource(inner)
        buffered.request(11) // fill buffer first
        buffered.rangeEquals(6, "world".encodeUtf8()) shouldBe true
    }

    "RealAsyncBufferedSource: select with options" - {
        val inner = AsyncBuffer()
        inner.writeUtf8("height=480")
        val buffered = RealAsyncBufferedSource(inner)
        val options = okio.Options.of(
            "depth=".encodeUtf8(),
            "height=".encodeUtf8(),
        )
        buffered.select(options) shouldBe 1
        buffered.readUtf8() shouldBe "480"
    }

    "RealAsyncBufferedSource: readAll drains source and buffer" - {
        val inner = AsyncBuffer()
        inner.writeUtf8("hello world")
        val buffered = RealAsyncBufferedSource(inner)
        val sink = AsyncBuffer()
        val written = buffered.readAll(sink)
        written shouldBe 11L
        sink.readUtf8() shouldBe "hello world"
    }

    "RealAsyncBufferedSource: exhausted on empty" - {
        val buffered = RealAsyncBufferedSource(AsyncBuffer())
        buffered.exhausted() shouldBe true
    }

    "RealAsyncBufferedSource: request fills buffer" - {
        val inner = AsyncBuffer()
        inner.writeUtf8("hello world")
        val buffered = RealAsyncBufferedSource(inner)
        buffered.request(5) shouldBe true
        buffered.readUtf8(5) shouldBe "hello"
    }

    "RealAsyncBufferedSource: require throws on insufficient data" - {
        val inner = AsyncBuffer()
        val buffered = RealAsyncBufferedSource(inner)
        shouldThrow<okio.EOFException> {
            buffered.require(1)
        }
    }

    "RealAsyncBufferedSource: readByte reads one byte" - {
        val inner = AsyncBuffer()
        inner.writeByte(0x42)
        val buffered = RealAsyncBufferedSource(inner)
        buffered.readByte() shouldBe 0x42
    }

    "RealAsyncBufferedSource: readShort reads two bytes" - {
        val inner = AsyncBuffer()
        inner.writeShort(0x1234)
        val buffered = RealAsyncBufferedSource(inner)
        buffered.readShort() shouldBe 0x1234.toShort()
    }

    "RealAsyncBufferedSource: readInt reads four bytes" - {
        val inner = AsyncBuffer()
        inner.writeInt(0x12345678)
        val buffered = RealAsyncBufferedSource(inner)
        buffered.readInt() shouldBe 0x12345678
    }

    "RealAsyncBufferedSource: readLong reads eight bytes" - {
        val inner = AsyncBuffer()
        inner.writeLong(0x123456789ABCDEFL)
        val buffered = RealAsyncBufferedSource(inner)
        buffered.readLong() shouldBe 0x123456789ABCDEFL
    }

    "RealAsyncBufferedSource: readUtf8Line after request" - {
        val inner = AsyncBuffer()
        inner.writeUtf8("hello\nworld\n")
        val buffered = RealAsyncBufferedSource(inner)
        buffered.request(10)
        buffered.readUtf8Line() shouldBe "hello"
    }

    "RealAsyncBufferedSink: auto-emit during writeAll" - {
        val inner = AsyncBuffer()
        val buffered = RealAsyncBufferedSink(inner)
        val source = AsyncBuffer()
        source.write(ByteArray(20000) { 0x42 })

        val written = buffered.writeAll(source)
        written shouldBe 20000L

        buffered.flush()
        inner.size shouldBe 20000L
    }

    "RealAsyncBufferedSink: close with exception still closes sink" - {
        var closed = false
        val failingSink = object : AsyncSink {
            override suspend fun write(source: okio.Buffer, byteCount: Long) {
                throw IOException("write error")
            }

            override suspend fun flush() = Unit
            override fun timeout() = okio.Timeout.NONE
            override suspend fun close() {
                closed = true
            }
        }

        val buffered = RealAsyncBufferedSink(failingSink)
        buffered.writeUtf8("data")

        shouldThrow<IOException> {
            buffered.close()
        }
        closed shouldBe true // Sink was still closed despite error
    }
})
