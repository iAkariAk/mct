package mct.util.aio

import okio.ByteString
import okio.FileMetadata
import okio.IOException
import okio.Options
import okio.Path
import okio.Timeout
import okio.TypedOptions

/**
 * A suspend equivalent of Okio's [okio.FileSystem].
 *
 * Every abstract method mirrors an Okio `FileSystem` method with the same
 * signature except that all IO-bound methods are `suspend`. Non-abstract
 * methods ([metadata], [listOrNull], [listRecursively], [createDirectories])
 * provide default implementations that subclasses may override.
 */
abstract class AsyncFileSystem : AsyncCloseable {

    // ---- path / metadata -------------------------------------------------

    abstract suspend fun canonicalize(path: Path): Path
    abstract suspend fun metadataOrNull(path: Path): FileMetadata?

    suspend fun metadata(path: Path): FileMetadata {
        return metadataOrNull(path) ?: throw IOException("file not found: $path")
    }

    /** Returns true if [path] exists and is readable. */
    open suspend fun exists(path: Path): Boolean = metadataOrNull(path) != null

    // ---- listing ---------------------------------------------------------

    abstract suspend fun list(dir: Path): List<Path>

    open suspend fun listOrNull(dir: Path): List<Path>? {
        return try {
            list(dir)
        } catch (_: IOException) {
            null
        }
    }

    open suspend fun listRecursively(
        dir: Path,
        followSymlinks: Boolean = false,
    ): Sequence<Path> {
        val result = mutableListOf<Path>()
        listRecursively(dir, result)
        return result.asSequence()
    }

    private suspend fun listRecursively(dir: Path, result: MutableList<Path>) {
        val children = list(dir)
        for (child in children) {
            result.add(child)
            val meta = metadataOrNull(child)
            if (meta != null && meta.isDirectory == true) {
                listRecursively(child, result)
            }
        }
    }

    // ---- file handles ----------------------------------------------------

    abstract suspend fun openReadOnly(file: Path): AsyncFileHandle
    abstract suspend fun openReadWrite(
        file: Path,
        mustCreate: Boolean = false,
        mustExist: Boolean = false,
    ): AsyncFileHandle

    // ---- streaming IO ----------------------------------------------------

    abstract suspend fun source(file: Path): AsyncSource
    abstract suspend fun sink(file: Path, mustCreate: Boolean = false): AsyncSink
    abstract suspend fun appendingSink(file: Path, mustExist: Boolean = false): AsyncSink

    /** Creates a source to read [file], executes [readerAction] to read it, and then closes the source. */
    suspend inline fun <T> read(file: Path, readerAction: AsyncBufferedSource.() -> T): T {
        val source = source(file).buffer()
        return source.use { it.readerAction() }
    }

    /** Creates a sink to write [file], executes [writerAction] to write it, and then closes the sink. */
    suspend inline fun <T> write(
        file: Path,
        mustCreate: Boolean = false,
        writerAction: AsyncBufferedSink.() -> T,
    ): T {
        val sink = sink(file, mustCreate).buffer()
        return sink.use { it.writerAction() }
    }

    // ---- directory operations --------------------------------------------

    abstract suspend fun createDirectory(dir: Path, mustCreate: Boolean = false)

    open suspend fun createDirectories(dir: Path, mustCreate: Boolean = false) {
        val parent = dir.parent
        if (parent != null) {
            createDirectories(parent, mustCreate = false)
        }
        createDirectory(dir, mustCreate)
    }

    // ---- delete / copy / move --------------------------------------------

    abstract suspend fun delete(path: Path, mustExist: Boolean = false)
    abstract suspend fun deleteRecursively(fileOrDirectory: Path, mustExist: Boolean = false)
    abstract suspend fun copy(source: Path, target: Path)
    abstract suspend fun atomicMove(source: Path, target: Path)
    abstract suspend fun createSymlink(source: Path, target: Path)

    // ---- lifecycle -------------------------------------------------------

    override abstract suspend fun close()

    companion object {
        /** Returns a writable temporary directory on the system file system. */
        val SYSTEM_TEMPORARY_DIRECTORY: Path = okio.FileSystem.SYSTEM_TEMPORARY_DIRECTORY
    }
}

/**
 * Wraps this [AsyncSource] into an [AsyncBufferedSource] with a lazy buffer.
 */
fun AsyncSource.buffer(): AsyncBufferedSource = RealAsyncBufferedSource(this)

/**
 * Wraps this [AsyncSink] into an [AsyncBufferedSink] that flushes through to this sink.
 */
fun AsyncSink.buffer(): AsyncBufferedSink = RealAsyncBufferedSink(this)

// ═════════════════════════════════════════════════════════════════════
// RealAsyncBufferedSource — matches Okio's RealBufferedSource behavior
// ═════════════════════════════════════════════════════════════════════

internal class RealAsyncBufferedSource(
    private val source: AsyncSource,
) : AsyncBufferedSource {

    internal val buf = AsyncBuffer()
    internal var closed = false

    override fun exhausted(): Boolean {
        check(!closed) { "closed" }
        return buf.exhausted()
    }

    override fun buffer(): AsyncBuffer = buf

    override suspend fun require(byteCount: Long) {
        if (!request(byteCount)) throw okio.EOFException()
    }

    override suspend fun request(byteCount: Long): Boolean {
        check(!closed) { "closed" }
        while (buf.size < byteCount) {
            if (source.read(buf.okioBuffer, SegmentSize) == -1L) return false
        }
        return true
    }

    override suspend fun readByte(): Byte { require(1); return buf.readByte() }
    override suspend fun readShort(): Short { require(2); return buf.readShort() }
    override suspend fun readShortLe(): Short { require(2); return buf.readShortLe() }
    override suspend fun readInt(): Int { require(4); return buf.readInt() }
    override suspend fun readIntLe(): Int { require(4); return buf.readIntLe() }
    override suspend fun readLong(): Long { require(8); return buf.readLong() }
    override suspend fun readLongLe(): Long { require(8); return buf.readLongLe() }

    override suspend fun readDecimalLong(): Long {
        require(1)
        var pos = 0L
        while (request(pos + 1)) {
            val b = buf.okioBuffer[pos]
            if ((b < '0'.code.toByte() || b > '9'.code.toByte()) && (pos != 0L || b != '-'.code.toByte())) {
                if (pos == 0L) throw NumberFormatException("Expected a digit or '-' but was 0x${b.toString(16)}")
                break
            }
            pos++
        }
        return buf.readDecimalLong()
    }

    override suspend fun readHexadecimalUnsignedLong(): Long {
        require(1)
        var pos = 0
        while (request((pos + 1).toLong())) {
            val b = buf.okioBuffer[pos.toLong()]
            if ((b < '0'.code.toByte() || b > '9'.code.toByte()) &&
                (b < 'a'.code.toByte() || b > 'f'.code.toByte()) &&
                (b < 'A'.code.toByte() || b > 'F'.code.toByte())
            ) {
                if (pos == 0) throw NumberFormatException("Expected leading [0-9a-fA-F] character but was 0x${b.toString(16)}")
                break
            }
            pos++
        }
        return buf.readHexadecimalUnsignedLong()
    }

    override suspend fun skip(byteCount: Long) {
        check(!closed) { "closed" }
        var remaining = byteCount
        while (remaining > 0) {
            if (buf.size == 0L && source.read(buf.okioBuffer, SegmentSize) == -1L) {
                throw okio.EOFException()
            }
            val toSkip = minOf(remaining, buf.size)
            buf.skip(toSkip)
            remaining -= toSkip
        }
    }

    override suspend fun readUtf8(): String {
        drainSourceToBuffer()
        return buf.readUtf8()
    }

    override suspend fun readUtf8(byteCount: Long): String { require(byteCount); return buf.readUtf8(byteCount) }

    override suspend fun readUtf8Line(): String? {
        val newline = indexOf('\n'.code.toByte())
        return if (newline == -1L) {
            if (buf.size != 0L) {
                val result = buf.readUtf8()
                if (result.isEmpty()) null else result
            } else {
                null
            }
        } else {
            buf.readUtf8Line()
        }
    }

    override suspend fun readUtf8LineStrict(): String = readUtf8LineStrict(Long.MAX_VALUE)

    override suspend fun readUtf8LineStrict(limit: Long): String {
        if (limit < 0) throw IllegalArgumentException("limit < 0: $limit")
        val scanLength = if (limit == Long.MAX_VALUE) Long.MAX_VALUE else limit + 1L
        val newline = indexOf('\n'.code.toByte(), 0, scanLength)
        if (newline != -1L) {
            val line = buf.readUtf8(newline)
            buf.skip(1)
            return line
        }
        if (scanLength < Long.MAX_VALUE &&
            request(scanLength) && buf.okioBuffer[scanLength - 1] == '\r'.code.toByte() &&
            request(scanLength + 1) && buf.okioBuffer[scanLength] == '\n'.code.toByte()
        ) {
            val line = buf.readUtf8(scanLength - 1)
            buf.skip(2)
            return line
        }
        val data = okio.Buffer()
        buf.okioBuffer.copyTo(data, 0, minOf(32, buf.size))
        throw okio.EOFException(
            "\\n not found: limit=" + minOf(buf.size, limit) +
                " content=" + data.readByteString().hex() + '…'.toString(),
        )
    }

    override suspend fun readUtf8CodePoint(): Int {
        require(1)
        val b0 = buf.okioBuffer[0].toInt()
        when {
            b0 and 0xe0 == 0xc0 -> require(2)
            b0 and 0xf0 == 0xe0 -> require(3)
            b0 and 0xf8 == 0xf0 -> require(4)
        }
        return buf.readUtf8CodePoint()
    }

    override suspend fun readByteString(): ByteString {
        drainSourceToBuffer()
        return buf.readByteString()
    }

    override suspend fun readByteString(byteCount: Long): ByteString { require(byteCount); return buf.readByteString(byteCount) }

    override suspend fun readByteArray(): ByteArray {
        drainSourceToBuffer()
        return buf.readByteArray()
    }

    private suspend fun drainSourceToBuffer() {
        val temp = okio.Buffer()
        while (true) {
            val read = source.read(temp, SegmentSize)
            if (read == -1L) break
            buf.okioBuffer.write(temp, read)
        }
    }

    override suspend fun readByteArray(byteCount: Long): ByteArray { require(byteCount); return buf.readByteArray(byteCount) }

    override suspend fun read(sink: ByteArray): Int {
        if (buf.size == 0L) {
            if (sink.isEmpty()) return 0
            val read = source.read(buf.okioBuffer, SegmentSize)
            if (read == -1L) return -1
        }
        val toRead = minOf(sink.size.toLong(), buf.size).toInt()
        return buf.okioBuffer.read(sink, 0, toRead)
    }

    override suspend fun readFully(sink: AsyncBuffer, byteCount: Long) {
        try {
            require(byteCount)
        } catch (e: okio.EOFException) {
            sink.okioBuffer.writeAll(buf.okioBuffer)
            throw e
        }
        buf.okioBuffer.readFully(sink.okioBuffer, byteCount)
    }

    override suspend fun readFully(sink: ByteArray) {
        try {
            require(sink.size.toLong())
        } catch (e: okio.EOFException) {
            var offset = 0
            while (buf.size > 0L) {
                val read = buf.okioBuffer.read(sink, offset, buf.size.toInt())
                if (read == -1) throw AssertionError()
                offset += read
            }
            throw e
        }
        buf.okioBuffer.readFully(sink)
    }

    override suspend fun readInto(array: ByteArray, offset: Int, byteCount: Int): Int {
        if (buf.size == 0L) {
            if (byteCount == 0) return 0
            val read = source.read(buf.okioBuffer, SegmentSize)
            if (read == -1L) return -1
        }
        val toRead = minOf(byteCount.toLong(), buf.size).toInt()
        return buf.okioBuffer.read(array, offset, toRead)
    }

    override suspend fun readAll(sink: AsyncSink): Long {
        var total = 0L
        // First drain any buffered data
        if (buf.size > 0L) {
            total += buf.size
            sink.write(buf.okioBuffer, buf.size)
        }
        // Then drain source
        val temp = okio.Buffer()
        while (true) {
            val read = source.read(temp, SegmentSize)
            if (read == -1L) break
            total += read
            if (sink is AsyncBuffer) {
                sink.okioBuffer.write(temp, read)
            } else {
                sink.write(temp, read)
            }
        }
        return total
    }

    override suspend fun select(options: Options): Int {
        check(!closed) { "closed" }
        while (true) {
            val result = buf.select(options)
            if (result != -1) return result
            if (source.read(buf.okioBuffer, SegmentSize) == -1L) return -1
        }
    }

    override suspend fun <T : Any> select(options: TypedOptions<T>): T? {
        check(!closed) { "closed" }
        while (true) {
            val result = buf.select(options)
            if (result != null) return result
            if (source.read(buf.okioBuffer, SegmentSize) == -1L) return null
        }
    }

    override suspend fun indexOf(byte: Byte): Long = indexOf(byte, 0, Long.MAX_VALUE)

    override suspend fun indexOf(byte: Byte, fromIndex: Long): Long = indexOf(byte, fromIndex, Long.MAX_VALUE)

    override suspend fun indexOf(byte: Byte, fromIndex: Long, toIndex: Long): Long {
        check(!closed) { "closed" }
        var start = fromIndex
        while (start < toIndex) {
            val result = buf.okioBuffer.indexOf(byte, start, toIndex)
            if (result != -1L) return result
            val lastSize = buf.size
            if (lastSize >= toIndex || source.read(buf.okioBuffer, SegmentSize) == -1L) return -1L
            start = maxOf(start, lastSize)
        }
        return -1L
    }

    override suspend fun indexOf(bytes: ByteString): Long = indexOf(bytes, 0, Long.MAX_VALUE)

    override suspend fun indexOf(bytes: ByteString, fromIndex: Long): Long = indexOf(bytes, fromIndex, Long.MAX_VALUE)

    override suspend fun indexOf(bytes: ByteString, fromIndex: Long, toIndex: Long): Long {
        check(!closed) { "closed" }
        var start = fromIndex
        while (true) {
            val result = buf.okioBuffer.indexOf(bytes, start)
            if (result != -1L && (toIndex == Long.MAX_VALUE || result < toIndex)) return result
            val lastSize = buf.size
            if (lastSize >= toIndex || source.read(buf.okioBuffer, SegmentSize) == -1L) return -1L
            start = maxOf(start, lastSize)
        }
    }

    override suspend fun indexOfElement(targetBytes: ByteString): Long = indexOfElement(targetBytes, 0)

    override suspend fun indexOfElement(targetBytes: ByteString, fromIndex: Long): Long {
        check(!closed) { "closed" }
        var start = fromIndex
        while (true) {
            val result = buf.okioBuffer.indexOfElement(targetBytes, start)
            if (result != -1L) return result
            val lastSize = buf.size
            if (source.read(buf.okioBuffer, SegmentSize) == -1L) return -1L
            start = maxOf(start, lastSize)
        }
    }

    override fun rangeEquals(offset: Long, bytes: ByteString): Boolean =
        buf.rangeEquals(offset, bytes)

    override fun rangeEquals(offset: Long, bytes: ByteString, bytesOffset: Int, byteCount: Int): Boolean =
        buf.rangeEquals(offset, bytes, bytesOffset, byteCount)

    override fun peek(): AsyncBufferedSource = RealAsyncBufferedSource(PeekAsyncSource(source, buf))

    override suspend fun read(sink: okio.Buffer, byteCount: Long): Long {
        if (buf.size == 0L) {
            val read = source.read(buf.okioBuffer, SegmentSize)
            if (read == -1L) return -1L
        }
        val toRead = minOf(byteCount, buf.size)
        return buf.okioBuffer.read(sink, toRead)
    }

    override fun timeout() = source.timeout()

    override suspend fun close() {
        if (closed) return
        closed = true
        source.close()
        buf.clear()
    }
}

/**
 * A PeekSource-like wrapper that reads from the original source but accounts
 * for data already consumed into the buffer.
 */
private class PeekAsyncSource(
    private val source: AsyncSource,
    private val buffer: AsyncBuffer,
) : AsyncSource {
    private var pos = 0L

    override suspend fun read(sink: okio.Buffer, byteCount: Long): Long {
        // Serve from buffer first
        if (pos < buffer.size) {
            val available = buffer.size - pos
            val toRead = minOf(byteCount, available)
            buffer.okioBuffer.copyTo(sink, pos, toRead)
            pos += toRead
            return toRead
        }
        // Then from source
        return source.read(sink, byteCount)
    }

    override fun timeout(): Timeout = source.timeout()

    override suspend fun close() = Unit // peek source doesn't close the original
}

// ═════════════════════════════════════════════════════════════════════
// RealAsyncBufferedSink — matches Okio's RealBufferedSink behavior
// ═════════════════════════════════════════════════════════════════════

internal class RealAsyncBufferedSink(
    private val sink: AsyncSink,
) : AsyncBufferedSink {

    internal val buf = AsyncBuffer()
    internal var closed = false

    override fun buffer(): AsyncBuffer = buf

    override suspend fun emit(): AsyncBufferedSink {
        check(!closed) { "closed" }
        val byteCount = buf.size
        if (byteCount > 0L) sink.write(buf.okioBuffer, byteCount)
        return this
    }

    override suspend fun emitCompleteSegments(): AsyncBufferedSink {
        check(!closed) { "closed" }
        val byteCount = buf.completeSegmentByteCount()
        if (byteCount > 0L) sink.write(buf.okioBuffer, byteCount)
        return this
    }

    override suspend fun writeByte(b: Int): AsyncBufferedSink {
        check(!closed) { "closed" }
        buf.writeByte(b)
        return emitCompleteSegments()
    }

    override suspend fun writeShort(s: Int): AsyncBufferedSink {
        check(!closed) { "closed" }
        buf.writeShort(s)
        return emitCompleteSegments()
    }

    override suspend fun writeShortLe(s: Int): AsyncBufferedSink {
        check(!closed) { "closed" }
        buf.writeShortLe(s)
        return emitCompleteSegments()
    }

    override suspend fun writeInt(i: Int): AsyncBufferedSink {
        check(!closed) { "closed" }
        buf.writeInt(i)
        return emitCompleteSegments()
    }

    override suspend fun writeIntLe(i: Int): AsyncBufferedSink {
        check(!closed) { "closed" }
        buf.writeIntLe(i)
        return emitCompleteSegments()
    }

    override suspend fun writeLong(l: Long): AsyncBufferedSink {
        check(!closed) { "closed" }
        buf.writeLong(l)
        return emitCompleteSegments()
    }

    override suspend fun writeLongLe(l: Long): AsyncBufferedSink {
        check(!closed) { "closed" }
        buf.writeLongLe(l)
        return emitCompleteSegments()
    }

    override suspend fun writeDecimalLong(v: Long): AsyncBufferedSink {
        check(!closed) { "closed" }
        buf.writeDecimalLong(v)
        return emitCompleteSegments()
    }

    override suspend fun writeHexadecimalUnsignedLong(v: Long): AsyncBufferedSink {
        check(!closed) { "closed" }
        buf.writeHexadecimalUnsignedLong(v)
        return emitCompleteSegments()
    }

    override suspend fun writeUtf8(string: String): AsyncBufferedSink {
        check(!closed) { "closed" }
        buf.writeUtf8(string)
        return emitCompleteSegments()
    }

    override suspend fun writeUtf8(string: String, beginIndex: Int, endIndex: Int): AsyncBufferedSink {
        check(!closed) { "closed" }
        buf.writeUtf8(string, beginIndex, endIndex)
        return emitCompleteSegments()
    }

    override suspend fun writeUtf8CodePoint(codePoint: Int): AsyncBufferedSink {
        check(!closed) { "closed" }
        buf.writeUtf8CodePoint(codePoint)
        return emitCompleteSegments()
    }

    override suspend fun writeByteString(byteString: ByteString): AsyncBufferedSink {
        check(!closed) { "closed" }
        buf.writeByteString(byteString)
        return emitCompleteSegments()
    }

    override suspend fun write(source: okio.ByteString): AsyncBufferedSink {
        check(!closed) { "closed" }
        buf.write(source)
        return emitCompleteSegments()
    }

    override suspend fun write(source: okio.ByteString, offset: Int, byteCount: Int): AsyncBufferedSink {
        check(!closed) { "closed" }
        buf.write(source, offset, byteCount)
        return emitCompleteSegments()
    }

    override suspend fun write(source: ByteArray): AsyncBufferedSink {
        check(!closed) { "closed" }
        buf.write(source)
        return emitCompleteSegments()
    }

    override suspend fun write(source: ByteArray, offset: Int, byteCount: Int): AsyncBufferedSink {
        check(!closed) { "closed" }
        buf.write(source, offset, byteCount)
        return emitCompleteSegments()
    }

    override suspend fun write(source: okio.Buffer, byteCount: Long) {
        check(!closed) { "closed" }
        buf.write(source, byteCount)
        emitCompleteSegments()
    }

    override suspend fun writeAll(source: AsyncSource): Long {
        var total = 0L
        val temp = okio.Buffer()
        while (true) {
            val read = source.read(temp, SegmentSize)
            if (read == -1L) break
            total += read
            buf.write(temp, read)
            emitCompleteSegments()
        }
        return total
    }

    override suspend fun write(source: AsyncSource, byteCount: Long): AsyncBufferedSink {
        var remaining = byteCount
        val temp = okio.Buffer()
        while (remaining > 0L) {
            val read = source.read(temp, remaining)
            if (read == -1L) throw okio.EOFException()
            remaining -= read
            buf.okioBuffer.write(temp, read)
            emitCompleteSegments()
        }
        return this
    }

    override fun timeout(): Timeout = sink.timeout()

    override suspend fun flush() {
        check(!closed) { "closed" }
        if (buf.size > 0L) sink.write(buf.okioBuffer, buf.size)
        sink.flush()
    }

    override suspend fun close() {
        if (closed) return
        var thrown: Throwable? = null
        try {
            if (buf.size > 0L) sink.write(buf.okioBuffer, buf.size)
        } catch (e: Throwable) {
            thrown = e
        }
        try {
            sink.close()
        } catch (e: Throwable) {
            if (thrown == null) thrown = e
        }
        closed = true
        if (thrown != null) throw thrown
    }
}

internal const val SegmentSize = 8192L

// ── Okio FileSystem → AsyncFileSystem adapter ─────────────────────

internal class BlockingFileSystemAsAsyncFileSystem(
    private val fs: okio.FileSystem,
) : AsyncFileSystem() {

    override suspend fun canonicalize(path: Path): Path = fs.canonicalize(path)
    override suspend fun metadataOrNull(path: Path): FileMetadata? = fs.metadataOrNull(path)

    override suspend fun list(dir: Path): List<Path> = fs.list(dir)

    override suspend fun listOrNull(dir: Path): List<Path>? = fs.listOrNull(dir)

    override suspend fun openReadOnly(file: Path): AsyncFileHandle =
        fs.openReadOnly(file).zio()

    override suspend fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean): AsyncFileHandle =
        fs.openReadWrite(file, mustCreate, mustExist).zio()

    override suspend fun source(file: Path): AsyncSource = fs.source(file).zio()

    override suspend fun sink(file: Path, mustCreate: Boolean): AsyncSink =
        fs.sink(file, mustCreate).zio()

    override suspend fun appendingSink(file: Path, mustExist: Boolean): AsyncSink =
        fs.appendingSink(file, mustExist).zio()

    override suspend fun createDirectory(dir: Path, mustCreate: Boolean) =
        fs.createDirectory(dir, mustCreate)

    override suspend fun delete(path: Path, mustExist: Boolean) = fs.delete(path, mustExist)

    override suspend fun deleteRecursively(fileOrDirectory: Path, mustExist: Boolean) =
        fs.deleteRecursively(fileOrDirectory, mustExist)

    override suspend fun copy(source: Path, target: Path) = fs.copy(source, target)
    override suspend fun atomicMove(source: Path, target: Path) = fs.atomicMove(source, target)
    override suspend fun createSymlink(source: Path, target: Path) = fs.createSymlink(source, target)
    override suspend fun close() = fs.close()
}
