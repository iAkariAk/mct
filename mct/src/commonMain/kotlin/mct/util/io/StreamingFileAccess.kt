package mct.util.io

import no.synth.kmpzip.zip.ZipEntry
import okio.*
import okio.Path.Companion.toPath

data class StreamingFile(
    val path: Path,
    val size: Long,
    val time: Long,
)

sealed interface StreamingFileOperation {
    val file: StreamingFile
}


data class StreamingFileReading(
    override val file: StreamingFile,
    val source: Pair<() -> BufferedSource, /*close:*/ (BufferedSource) -> Unit>,
) : StreamingFileOperation

data class StreamingFileWriting(
    override val file: StreamingFile,
    val source: Pair<() -> BufferedSource, /*close:*/ (BufferedSource) -> Unit>,
    val sink: Pair<() -> BufferedSink, /*close:*/(BufferedSink) -> Unit>,
    val onFailure: (Throwable) -> Unit,
) : StreamingFileOperation

private fun Path.toStreamingFile(metadata: FileMetadata): StreamingFile {
    val time = maxOf(
        metadata.createdAtMillis ?: 0,
        maxOf(metadata.lastModifiedAtMillis ?: 0, metadata.lastAccessedAtMillis ?: 0)
    )
    return StreamingFile(
        path = this,
        size = metadata.size ?: 0,
        time = time,
    )
}

private fun ZipEntry.toStreamingFile(): StreamingFile =
    StreamingFile(name.toPath(true), size, time)

class WalkStream<T : StreamingFileOperation>(
    delegated: Sequence<T>, private val _close: () -> Unit,
) : Sequence<T> by delegated, AutoCloseable {
    override fun close() = _close()
}

inline fun <T : StreamingFileOperation> Sequence<T>.closable(noinline close: () -> Unit) = WalkStream(this, close)

interface StreamingFileWalk {
    fun read(predicate: (StreamingFile) -> Boolean): WalkStream<StreamingFileReading>
    fun write(predicate: (StreamingFile) -> Boolean): WalkStream<StreamingFileWriting>
}


fun FileSystem.walkDirectory(dir: Path): StreamingFileWalk {
    require(metadata(dir).isDirectory)
    return FileSystemFileWalkStream(dir, newRelativeFS(dir))
}

private class FileSystemFileWalkStream(private val dir: Path, private val fs: FileSystem) : StreamingFileWalk {
    private fun walk() = fs.listRecursively(dir).mapNotNull {
        val metadata = fs.metadata(it)
        if (metadata.isDirectory) return@mapNotNull null
        it.toStreamingFile(metadata)
    }

    override fun read(predicate: (StreamingFile) -> Boolean): WalkStream<StreamingFileReading> =
        walk().filter(predicate).map { file ->
            StreamingFileReading(
                file,
                { fs.source(file.path).buffer() } to BufferedSource::close
            )
        }.closable { }

    override fun write(predicate: (StreamingFile) -> Boolean): WalkStream<StreamingFileWriting> =
        walk().filter(predicate).map { file ->
            var isRead = false
            StreamingFileWriting(
                file,
                { fs.source(file.path).buffer() } to {
                    it.close()
                    isRead = true
                },
                {
                    require(isRead)
                    fs.sink(file.path).buffer()
                } to BufferedSink::close
            ) {} // Don't handle onFailure because sink must be got before source is close
        }.closable { }
}

fun FileSystem.walkZip(zip: Path): StreamingFileWalk = ZipFileWalkStream(zip, this)

private class ZipFileWalkStream(private val zip: Path, private val fs: FileSystem) : StreamingFileWalk {
    override fun read(predicate: (StreamingFile) -> Boolean): WalkStream<StreamingFileReading> {
        val zis = fs.openZipInputStream(zip)
        val source = zis.source().buffer()
        return zis.walk()
            .map(ZipEntry::toStreamingFile)
            .filter(predicate)
            .map { StreamingFileReading(it, { source } to {}) }.closable(zis::close)
    }

    override fun write(predicate: (StreamingFile) -> Boolean): WalkStream<StreamingFileWriting> {
        val tmpZip = zip.temp()
        fs.atomicMove(zip, tmpZip)
        val zis = fs.openZipInputStream(tmpZip)
        val zos = fs.openZipOutputStream(zip)
        val source = zis.source().buffer()
        val sink = zos.sink().buffer()
        return zis.walk()
            .filter { !it.isDirectory }
            .map(ZipEntry::toStreamingFile)
            .mapNotNull {
                zos.putNextEntry(ZipEntry(it.path.toString()))
                if (predicate(it)) {
                    StreamingFileWriting(it, { source } to {}, { sink } to { s -> s.flush(); zos.closeEntry() }) {
                        zos.close()
                        zis.close()
                        fs.atomicMove(tmpZip, zip)
                        throw it
                    }
                } else {
                    source.readAll(sink)
                    sink.flush()
                    zos.closeEntry()
                    null
                }
            }.closable {
                zos.close()
                zis.close()
                fs.delete(tmpZip)
            }

    }
}


private fun Path.temp(): Path = appendAfterName("._tmp")