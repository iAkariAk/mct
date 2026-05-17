package mct.util.aio

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class AsyncFileHandleTest : FreeSpec({

    "read/write basic" - {
        val handle = asyncFileHandleFrom(byteArrayOf(1, 2, 3, 4, 5))

        val buf = ByteArray(5)
        val read = handle.read(0, buf, 0, 5)
        read shouldBe 5
        buf shouldBe byteArrayOf(1, 2, 3, 4, 5)
    }

    "read partial" - {
        val handle = asyncFileHandleFrom(byteArrayOf(1, 2, 3, 4, 5))

        val buf = ByteArray(3)
        val read = handle.read(1, buf, 0, 3)
        read shouldBe 3
        buf shouldBe byteArrayOf(2, 3, 4)
    }

    "read returns -1 at EOF" - {
        val handle = asyncFileHandleFrom(byteArrayOf(1, 2))

        val buf = ByteArray(5)
        val read = handle.read(10, buf, 0, 5)
        read shouldBe -1
    }

    "write data" - {
        val handle = asyncFileHandleFrom(ByteArray(10))

        handle.write(0, byteArrayOf(1, 2, 3), 0, 3)
        handle.size() shouldBe 10L

        val buf = ByteArray(3)
        handle.read(0, buf, 0, 3)
        buf shouldBe byteArrayOf(1, 2, 3)
    }

    "write extends file size" - {
        val handle = asyncFileHandleFrom(ByteArray(5))

        handle.write(0, ByteArray(100) { 0x42 })
        handle.size() shouldBe 100L
    }

    "write to read-only handle throws" - {
        val handle = AsyncFileHandle(object : AsyncFileHandle.Delegate {
            override suspend fun read(position: Long, array: ByteArray, offset: Int, byteCount: Int) = 0
            override suspend fun write(position: Long, array: ByteArray, offset: Int, byteCount: Int) = Unit
            override suspend fun size(): Long = 3L
            override suspend fun resize(length: Long) = Unit
            override suspend fun flush() = Unit
            override suspend fun close() = Unit
        }, readWrite = false)

        shouldThrow<IllegalStateException> {
            handle.write(0, byteArrayOf(1), 0, 1)
        }
    }

    "resize extends file" - {
        val handle = asyncFileHandleFrom(ByteArray(5))
        handle.resize(10)
        handle.size() shouldBe 10L
    }

    "resize shrinks file" - {
        val handle = asyncFileHandleFrom(ByteArray(100))
        handle.resize(10)
        handle.size() shouldBe 10L
    }

    "size returns file size" - {
        val handle = asyncFileHandleFrom(ByteArray(42))
        handle.size() shouldBe 42L
    }

    "readWrite property" - {
        val data = ByteArray(10)
        val readOnly = AsyncFileHandle(readOnlyDelegate(data), readWrite = false)
        readOnly.readWrite shouldBe false

        val readWrite = AsyncFileHandle(readWriteDelegate(data), readWrite = true)
        readWrite.readWrite shouldBe true
    }

    "flush on read-only handle throws" - {
        val handle = AsyncFileHandle(object : AsyncFileHandle.Delegate {
            override suspend fun read(position: Long, array: ByteArray, offset: Int, byteCount: Int) = 0
            override suspend fun write(position: Long, array: ByteArray, offset: Int, byteCount: Int) = Unit
            override suspend fun size(): Long = 0L
            override suspend fun resize(length: Long) = Unit
            override suspend fun flush() = Unit
            override suspend fun close() = Unit
        }, readWrite = false)

        shouldThrow<IllegalStateException> {
            handle.flush()
        }
    }

    "source creates a streaming source" - {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val handle = asyncFileHandleFrom(data)

        val src = handle.source(0)
        val buf = okio.Buffer()
        val read = src.read(buf, 5)
        read shouldBe 5
        buf.readByteArray() shouldBe data
    }

    "sink creates a streaming sink" - {
        val handle = asyncFileHandleFrom(ByteArray(10))

        val snk = handle.sink(0)
        snk.write(okio.Buffer().write(byteArrayOf(1, 2, 3)), 3)
        snk.flush()

        val buf = ByteArray(3)
        handle.read(0, buf, 0, 3)
        buf shouldBe byteArrayOf(1, 2, 3)
    }

    "appendingSink writes at end" - {
        val handle = asyncFileHandleFrom(ByteArray(10))

        val snk = handle.appendingSink()
        val okioBuf = okio.Buffer()
        okioBuf.writeUtf8("hello")
        snk.write(okioBuf, 5)
        snk.flush()

        handle.size() shouldBe 15L
    }

    "close" - {
        var closed = false
        val handle = AsyncFileHandle(object : AsyncFileHandle.Delegate {
            override suspend fun read(position: Long, array: ByteArray, offset: Int, byteCount: Int) = 0
            override suspend fun write(position: Long, array: ByteArray, offset: Int, byteCount: Int) = Unit
            override suspend fun size(): Long = 0L
            override suspend fun resize(length: Long) = Unit
            override suspend fun flush() = Unit
            override suspend fun close() {
                closed = true
            }
        })

        handle.close()
        closed shouldBe true
    }

    "read after close throws" - {
        val handle = asyncFileHandleFrom(ByteArray(10))
        handle.close()
        shouldThrow<IllegalStateException> {
            handle.read(0, ByteArray(1), 0, 1)
        }
    }

    "isOpen reflects state" - {
        val handle = asyncFileHandleFrom(ByteArray(10))
        handle.isOpen shouldBe true
        handle.close()
        handle.isOpen shouldBe false
    }

    "write with default parameters" - {
        val handle = asyncFileHandleFrom(ByteArray(10))

        handle.write(0, byteArrayOf(1, 2, 3))
        val buf = ByteArray(3)
        handle.read(0, buf)
        buf shouldBe byteArrayOf(1, 2, 3)
    }

    "read with default parameters" - {
        val handle = asyncFileHandleFrom(byteArrayOf(10, 20, 30, 40, 50))

        val buf = ByteArray(3)
        val read = handle.read(0, buf)
        read shouldBe 3
        buf shouldBe byteArrayOf(10, 20, 30)
    }
})

private fun readOnlyDelegate(data: ByteArray) = object : AsyncFileHandle.Delegate {
    override suspend fun read(position: Long, array: ByteArray, offset: Int, byteCount: Int): Int {
        if (position >= data.size) return -1
        val count = minOf(byteCount.toLong(), data.size - position).toInt()
        data.copyInto(array, offset, position.toInt(), position.toInt() + count)
        return count
    }

    override suspend fun write(position: Long, array: ByteArray, offset: Int, byteCount: Int) =
        throw Exception("read-only")

    override suspend fun size(): Long = data.size.toLong()
    override suspend fun resize(length: Long) = throw Exception("read-only")
    override suspend fun flush() = Unit
    override suspend fun close() = Unit
}

private fun readWriteDelegate(data: ByteArray) = object : AsyncFileHandle.Delegate {
    private var storage = data.copyOf()

    override suspend fun read(position: Long, array: ByteArray, offset: Int, byteCount: Int): Int {
        if (position >= storage.size) return -1
        val count = minOf(byteCount.toLong(), storage.size - position).toInt()
        storage.copyInto(array, offset, position.toInt(), position.toInt() + count)
        return count
    }

    override suspend fun write(position: Long, array: ByteArray, offset: Int, byteCount: Int) {
        val newSize = maxOf(storage.size.toLong(), position + byteCount).toInt()
        storage = storage.copyOf(newSize)
        array.copyInto(storage, position.toInt(), offset, offset + byteCount)
    }

    override suspend fun size(): Long = storage.size.toLong()
    override suspend fun resize(length: Long) {
        storage = storage.copyOf(length.toInt())
    }

    override suspend fun flush() = Unit
    override suspend fun close() = Unit
}

private fun asyncFileHandleFrom(data: ByteArray): AsyncFileHandle =
    AsyncFileHandle(readWriteDelegate(data.copyOf()))
