package mct

import arrow.core.getOrElse
import arrow.core.raise.either
import com.goncalossilva.resources.Resource
import kotlinx.coroutines.*
import mct.kit.TranslationMapping
import mct.serializer.MCTJson
import mct.util.unreachable
import no.synth.kmpzip.io.InputStream
import no.synth.kmpzip.io.OutputStream
import no.synth.kmpzip.okio.SinkOutputStream
import no.synth.kmpzip.okio.asInputStream
import no.synth.kmpzip.zip.ZipInputStream
import no.synth.kmpzip.zip.safeEntrySegments
import okio.Buffer
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.fakefilesystem.FakeFileSystem
import kotlin.coroutines.CoroutineContext
import kotlin.properties.ReadOnlyProperty

private val test_map = Resource("TestMap.zip")
private val test_mappings = Resource("mappings.json")


private suspend fun copyStream(input: InputStream, output: OutputStream, buf: ByteArray) {
    while (true) {
        currentCoroutineContext().ensureActive()
        val n = input.read(buf, 0, buf.size)
        if (n == -1) break
        output.write(buf, 0, n)
    }
}

private suspend fun FileSystem.unzipFrom(
    from: ZipInputStream,
    target: Path,
    dispatcher: CoroutineContext = Dispatchers.IO,
) {
    withContext(dispatcher) {
        createDirectories(target)
        val buf = ByteArray(8192)
        while (true) {
            currentCoroutineContext().ensureActive()
            val entry = from.nextEntry ?: break
            val safe = safeEntrySegments(entry.name).fold(target) { acc, seg -> acc / seg }
            if (entry.isDirectory) {
                createDirectories(safe)
            } else {
                safe.parent?.let { createDirectories(it) }
                SinkOutputStream(sink(safe).buffer()).use { fos ->
                    copyStream(from, fos, buf)
                }
            }
        }
    }
}

suspend fun TestMapWorkspace(): MCTWorkspace {
    val buffer = Buffer()
    buffer.write(test_map.readBytes())
    val fs = FakeFileSystem()
    return ZipInputStream(buffer.asInputStream()).use { from ->
        fs.unzipFrom(from, ".".toPath())
        val env = Env(
            fs = fs.locked(),
            logger = Logger.Console()
        )
        either {
            MCTWorkspace("MCT Test".toPath(), env)
        }.getOrElse { unreachable }
    }
}


object TestFunctions {
    private fun mcf(name: String? = null) = ReadOnlyProperty<Any, String> { thisRef, property ->
        val name = name ?: property.name
        Resource("mcfunctions/$name.mcfunction").readText()
    }

    val update_billboard by mcf()
}

val mappings = MCTJson.decodeFromString<TranslationMapping>(test_mappings.readText())