package mct.util.aio

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

class AsyncBufferTest : FreeSpec({

    "empty buffer" - {
        val buf = AsyncBuffer()
        buf.size shouldBe 0L
        buf.exhausted() shouldBe true
        buf.snapshot() shouldBe ByteString.EMPTY
    }

    "write and read byte" - {
        val buf = AsyncBuffer()
        buf.writeByte(0x42)
        buf.size shouldBe 1L
        buf.readByte() shouldBe 0x42
        buf.size shouldBe 0L
    }

    "write and read short" - {
        val buf = AsyncBuffer()
        buf.writeShort(0x1234)
        buf.size shouldBe 2L
        buf.readShort() shouldBe 0x1234.toShort()
    }

    "write and read short little-endian" - {
        val buf = AsyncBuffer()
        buf.writeShortLe(0x1234)
        buf.readShortLe() shouldBe 0x1234.toShort()
    }

    "write and read int" - {
        val buf = AsyncBuffer()
        buf.writeInt(0x12345678)
        buf.readInt() shouldBe 0x12345678
    }

    "write and read int little-endian" - {
        val buf = AsyncBuffer()
        buf.writeIntLe(0x12345678)
        buf.readIntLe() shouldBe 0x12345678
    }

    "write and read long" - {
        val buf = AsyncBuffer()
        buf.writeLong(0x123456789ABCDEFL)
        buf.readLong() shouldBe 0x123456789ABCDEFL
    }

    "write and read long little-endian" - {
        val buf = AsyncBuffer()
        buf.writeLongLe(0x123456789ABCDEFL)
        buf.readLongLe() shouldBe 0x123456789ABCDEFL
    }

    "write and read utf8 string" - {
        val buf = AsyncBuffer()
        buf.writeUtf8("Hello, 世界!")
        buf.size shouldBe 14L
        buf.readUtf8() shouldBe "Hello, 世界!"
    }

    "write and read utf8 with byte count" - {
        val buf = AsyncBuffer()
        buf.writeUtf8("Hello, World!")
        buf.readUtf8(5) shouldBe "Hello"
        buf.readUtf8(2) shouldBe ", "
        buf.readUtf8() shouldBe "World!"
    }

    "writeUtf8 with beginIndex and endIndex" - {
        val buf = AsyncBuffer()
        buf.writeUtf8("Hello, World!", 7, 12)
        buf.readUtf8() shouldBe "World"
    }

    "writeUtf8CodePoint and readUtf8CodePoint" - {
        val buf = AsyncBuffer()
        buf.writeUtf8CodePoint(0x1F600) // 😀 emoji
        buf.readUtf8CodePoint() shouldBe 0x1F600
    }

    "readUtf8Line with newline" - {
        val buf = AsyncBuffer()
        buf.writeUtf8("line1\nline2\n")
        buf.readUtf8Line() shouldBe "line1"
        buf.readUtf8Line() shouldBe "line2"
        buf.readUtf8Line() shouldBe null
    }

    "readUtf8LineStrict" - {
        val buf = AsyncBuffer()
        buf.writeUtf8("line1\n")
        buf.readUtf8LineStrict() shouldBe "line1"
    }

    "readUtf8LineStrict with limit" - {
        val buf = AsyncBuffer()
        buf.writeUtf8("hello\n")
        buf.readUtf8LineStrict(10) shouldBe "hello"
    }

    "readUtf8LineStrict exceeding limit throws" - {
        val buf = AsyncBuffer()
        buf.writeUtf8("hello world\n")
        shouldThrow<okio.EOFException> {
            buf.readUtf8LineStrict(5)
        }
    }

    "readUtf8Line (no-arg) with CRLF" - {
        val buf = AsyncBuffer()
        buf.writeUtf8("line1\r\nline2\r\n")
        val first = buf.readUtf8Line()
        first shouldBe "line1"
        val second = buf.readUtf8Line()
        second shouldBe "line2"
    }

    "readUtf8Line without trailing newline" - {
        val buf = AsyncBuffer()
        buf.writeUtf8("just a line")
        buf.readUtf8Line() shouldBe "just a line"
    }

    "writeByteString" - {
        val buf = AsyncBuffer()
        val bs = "Hello".encodeUtf8()
        buf.writeByteString(bs)
        buf.readUtf8() shouldBe "Hello"
    }

    "write ByteString with offset and count" - {
        val buf = AsyncBuffer()
        val bs = "Hello, World!".encodeUtf8()
        buf.write(bs, 7, 5)
        buf.readUtf8() shouldBe "World"
    }

    "write ByteArray" - {
        val buf = AsyncBuffer()
        buf.write(byteArrayOf(1, 2, 3))
        buf.size shouldBe 3L
        buf.readByte() shouldBe 1
        buf.readByte() shouldBe 2
        buf.readByte() shouldBe 3
    }

    "readByteArray" - {
        val buf = AsyncBuffer()
        buf.write(byteArrayOf(1, 2, 3, 4, 5))
        buf.readByteArray(3) shouldBe byteArrayOf(1, 2, 3)
        buf.readByteArray() shouldBe byteArrayOf(4, 5)
    }

    "read (ByteArray) single param" - {
        val buf = AsyncBuffer()
        buf.write(byteArrayOf(10, 20, 30, 40))
        val sink = ByteArray(3)
        val read = buf.read(sink)
        read shouldBe 3
        sink shouldBe byteArrayOf(10, 20, 30)
    }

    "readByteString" - {
        val buf = AsyncBuffer()
        buf.writeUtf8("hello")
        val bs = buf.readByteString()
        bs.utf8() shouldBe "hello"
    }

    "readByteString with byte count" - {
        val buf = AsyncBuffer()
        buf.writeUtf8("hello world")
        val bs = buf.readByteString(5)
        bs.utf8() shouldBe "hello"
    }

    "readFully into AsyncBuffer" - {
        val buf = AsyncBuffer()
        buf.writeUtf8("hello world")
        val out = AsyncBuffer()
        buf.readFully(out, 5)
        out.readUtf8() shouldBe "hello"
        buf.readUtf8() shouldBe " world"
    }

    "readFully into ByteArray" - {
        val buf = AsyncBuffer()
        buf.write(byteArrayOf(1, 2, 3, 4, 5))
        val sink = ByteArray(5)
        buf.readFully(sink)
        sink shouldBe byteArrayOf(1, 2, 3, 4, 5)
    }

    "readFully partial throws" - {
        val buf = AsyncBuffer()
        buf.write(byteArrayOf(1, 2))
        shouldThrow<okio.EOFException> {
            buf.readFully(ByteArray(5))
        }
    }

    "readInto (read to ByteArray with offset)" - {
        val buf = AsyncBuffer()
        buf.write(byteArrayOf(10, 20, 30, 40, 50))
        val sink = ByteArray(5)
        val read = buf.readInto(sink, offset = 1, byteCount = 3)
        read shouldBe 3
        sink[1] shouldBe 10
        sink[2] shouldBe 20
        sink[3] shouldBe 30
    }

    "readAll to AsyncSink" - {
        val buf = AsyncBuffer()
        buf.writeUtf8("hello world")
        val out = AsyncBuffer()
        buf.readAll(out)
        out.readUtf8() shouldBe "hello world"
        buf.size shouldBe 0L
    }

    "select with options" - {
        val buf = AsyncBuffer()
        buf.writeUtf8("height=480")
        val options = okio.Options.of(
            "depth=".encodeUtf8(),
            "height=".encodeUtf8(),
        )
        val index = buf.select(options)
        index shouldBe 1
        buf.readUtf8() shouldBe "480"
    }

    "skip" - {
        val buf = AsyncBuffer()
        buf.writeUtf8("hello world")
        buf.skip(6)
        buf.readUtf8() shouldBe "world"
    }

    "indexOf byte" - {
        val buf = AsyncBuffer()
        buf.writeUtf8("hello world")
        buf.indexOf('o'.code.toByte()) shouldBe 4
    }

    "indexOf byte with fromIndex" - {
        val buf = AsyncBuffer()
        buf.writeUtf8("hello world")
        buf.indexOf('o'.code.toByte(), 5) shouldBe 7
    }

    "indexOf byte with fromIndex toIndex" - {
        val buf = AsyncBuffer()
        buf.writeUtf8("hello world")
        buf.indexOf('o'.code.toByte(), 0, 5) shouldBe 4
        buf.indexOf('o'.code.toByte(), 0, 4) shouldBe -1
    }

    "indexOf ByteString" - {
        val buf = AsyncBuffer()
        buf.writeUtf8("hello world")
        buf.indexOf("world".encodeUtf8()) shouldBe 6
    }

    "indexOf ByteString with fromIndex" - {
        val buf = AsyncBuffer()
        buf.writeUtf8("abc abc")
        buf.indexOf("abc".encodeUtf8(), 1) shouldBe 4
    }

    "indexOfElement" - {
        val buf = AsyncBuffer()
        buf.writeUtf8("hello world")
        buf.indexOfElement("aeiou".encodeUtf8()) shouldBe 1 // 'e'
    }

    "rangeEquals" - {
        val buf = AsyncBuffer()
        buf.writeUtf8("hello world")
        buf.rangeEquals(6, "world".encodeUtf8()) shouldBe true
        buf.rangeEquals(6, "worl".encodeUtf8(), 0, 4) shouldBe true
    }

    "peek" - {
        val buf = AsyncBuffer()
        buf.writeUtf8("hello world")

        val peek = buf.peek()
        peek.readUtf8(5) shouldBe "hello"

        // Original buffer unchanged
        buf.readUtf8(5) shouldBe "hello"
        buf.readUtf8() shouldBe " world"
    }

    "clear" - {
        val buf = AsyncBuffer()
        buf.writeUtf8("hello")
        buf.clear()
        buf.size shouldBe 0L
    }

    "snapshot" - {
        val buf = AsyncBuffer()
        buf.writeUtf8("hello")
        buf.snapshot() shouldBe "hello".encodeUtf8()
        buf.snapshot(3) shouldBe "hel".encodeUtf8()
    }

    "get (indexed access)" - {
        val buf = AsyncBuffer()
        buf.writeUtf8("ABC")
        buf[0] shouldBe 'A'.code.toByte()
        buf[1] shouldBe 'B'.code.toByte()
        buf[2] shouldBe 'C'.code.toByte()
    }

    "copyTo" - {
        val buf = AsyncBuffer()
        buf.writeUtf8("hello world")
        val out = AsyncBuffer()
        buf.copyTo(out, offset = 0, byteCount = 5)
        out.readUtf8() shouldBe "hello"
        // Original unchanged
        buf.readUtf8() shouldBe "hello world"
    }

    "completeSegmentByteCount" - {
        val buf = AsyncBuffer()
        buf.completeSegmentByteCount() shouldBe 0L
        buf.write(ByteArray(10000))
        buf.completeSegmentByteCount() shouldBe 8192L // one full segment
    }

    "copy (deep copy)" - {
        val buf = AsyncBuffer()
        buf.writeUtf8("hello")
        val copy = buf.copy()
        copy.readUtf8() shouldBe "hello"
        // Original still has the data
        buf.readUtf8() shouldBe "hello"
    }

    "hashing md5" - {
        val buf = AsyncBuffer()
        buf.writeUtf8("hello")
        buf.md5().hex() shouldBe "5d41402abc4b2a76b9719d911017c592"
    }

    "hashing sha1" - {
        val buf = AsyncBuffer()
        buf.writeUtf8("hello")
        buf.sha1().hex() shouldBe "aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d"
    }

    "hashing sha256" - {
        val buf = AsyncBuffer()
        buf.writeUtf8("hello")
        buf.sha256().hex() shouldBe "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
    }

    "writeDecimalLong and readDecimalLong" - {
        val buf = AsyncBuffer()
        buf.writeDecimalLong(12345)
        buf.readDecimalLong() shouldBe 12345L
    }

    "writeHexadecimalUnsignedLong and readHexadecimalUnsignedLong" - {
        val buf = AsyncBuffer()
        buf.writeHexadecimalUnsignedLong(0xDEADBEEF)
        buf.readHexadecimalUnsignedLong() shouldBe 0xDEADBEEF
    }

    "writeAll from AsyncSource" - {
        val src = AsyncBuffer()
        src.writeUtf8("source data")
        val dst = AsyncBuffer()
        dst.writeAll(src)
        dst.readUtf8() shouldBe "source data"
        src.size shouldBe 0L
    }

    "write source with byteCount limit" - {
        val src = AsyncBuffer()
        src.writeUtf8("hello world")
        val dst = AsyncBuffer()
        dst.write(src as AsyncSource, 5)
        dst.readUtf8() shouldBe "hello"
        src.readUtf8() shouldBe " world"
    }

    "buffer implements both source and sink" - {
        val buf = AsyncBuffer()
        buf.writeUtf8("test data")

        // Read via AsyncBufferedSource
        val src: AsyncBufferedSource = buf
        src.readUtf8(4) shouldBe "test"

        // Write via AsyncBufferedSink
        val sink: AsyncBufferedSink = buf
        sink.writeUtf8(" more")
        buf.readUtf8() shouldBe " data more"
    }

    "exhausted behavior" - {
        val buf = AsyncBuffer()
        buf.exhausted() shouldBe true
        buf.writeUtf8("data")
        buf.exhausted() shouldBe false
        buf.readUtf8()
        buf.exhausted() shouldBe true
    }

    "request and require" - {
        val buf = AsyncBuffer()
        buf.writeUtf8("hello")
        buf.request(3) shouldBe true
        buf.require(3)
        buf.readUtf8(3) shouldBe "hel"
    }

    "timeout" - {
        val buf = AsyncBuffer()
        buf.timeout() shouldBeSameInstanceAs okio.Timeout.NONE
    }

    "close" - {
        val buf = AsyncBuffer()
        buf.writeUtf8("data")
        buf.close()
        // Okio's Buffer.close() is a no-op; data is NOT cleared
        buf.size shouldBe 4L
    }

    "write from okio.Buffer" - {
        val buf = AsyncBuffer()
        val okioBuf = okio.Buffer()
        okioBuf.writeUtf8("external")
        buf.write(okioBuf, 8)
        buf.readUtf8() shouldBe "external"
    }
})
