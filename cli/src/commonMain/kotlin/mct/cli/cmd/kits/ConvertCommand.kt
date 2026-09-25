@file:OptIn(ExperimentalSerializationApi::class, InternalAPI::class)

package mct.cli.cmd.kits

import arrow.core.raise.Raise
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.restrictTo
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.json.okio.encodeToBufferedSink
import mct.FSHolder
import mct.MCTError
import mct.cli.BaseCommand
import mct.cli.enforceNot
import mct.cli.panic
import mct.cli.path
import mct.cli.util.CURRENT_PATH
import mct.serializer.MCTJson
import mct.serializer.NbtCommon
import mct.util.IO
import mct.util.formatir.IRElement
import mct.util.io.extension
import mct.util.io.stem
import mct.util.toRegex2
import mct.util.unreachable
import net.benwoodworth.knbt.*
import okio.BufferedSink
import okio.BufferedSource
import okio.Path
import okio.Path.Companion.toPath
import okio.use
import mct.serializer.Snbt as MCTSnbt

private var prettyOutput = false
private var nbtCompression: NbtCompression = NbtCompression.None
private var nbtCompressionLevel: Int? = null

private val CNbt
    get() = Nbt(NbtCommon) {
        compression = nbtCompression
        compressionLevel = nbtCompressionLevel
    }

private val CSnbt
    get() = StringifiedNbt(MCTSnbt) {
        prettyPrint = prettyOutput
    }

private val CJson
    get() = Json(MCTJson) {
        prettyPrint = prettyOutput
    }


private enum class ConvertableFormat(val display: String) {
    Json("json") {
        override fun decodeToIR(source: BufferedSource): IRElement =
            CJson.decodeFromBufferedSource<IRElement>(source)

        override fun encodeFromIR(sink: BufferedSink, value: IRElement) =
            CJson.encodeToBufferedSink(value, sink)
    },
    Snbt("snbt") {
        override fun decodeToIR(source: BufferedSource): IRElement =
            CSnbt.decodeFromString<IRElement>(source.use(BufferedSource::readUtf8))

        override fun encodeFromIR(sink: BufferedSink, value: IRElement): Unit = sink.use {
            sink.writeUtf8(CSnbt.encodeToString(value))
        }
    },
    Nbt("nbt") {
        override fun decodeToIR(source: BufferedSource): IRElement =
            CNbt.decodeFromSource<IRElement>(source)

        override fun encodeFromIR(sink: BufferedSink, value: IRElement): Unit =
            CNbt.encodeToSink(value, sink)
    },
    Auto("auto") {
        override fun decodeToIR(source: BufferedSource) = unreachable
        override fun encodeFromIR(sink: BufferedSink, value: IRElement) = unreachable
    };

    companion object {
        fun fromExt(ext: String): ConvertableFormat = when (ext) {
            "json" -> Json
            "snbt" -> Snbt
            "nbt", "dat" -> Nbt
            else -> panic("Unknown extension: $ext")
        }
    }

    abstract fun decodeToIR(source: BufferedSource): IRElement
    abstract fun encodeFromIR(sink: BufferedSink, value: IRElement)

    fun inferAuto(path: Path): ConvertableFormat =
        if (this != Auto) this else fromExt(path.extension)
}

class ConvertCommand : BaseCommand("convert", "Convert different formats") {
    private val input by option("--input", "-i", help = "Path to input file").required()
    private val regex by option("--regex", "-r", help = "Use regex to match input files").flag()

    private val output by option("--output", "-o", help = "Path to output file").path()
    private val inputFormat by option(
        "--input-format", "-if", help = "Input file format"
    ).enum<ConvertableFormat> { it.display }.default(Auto)
    private val outputFormat by option(
        "--output-format", "-of", help = "Output file format"
    ).enum<ConvertableFormat> { it.display }.default(Auto)

    private val compression by option(
        "--compression", "-c", help = "Compression kind"
    ).choice("none", "gzip", "zlib").convert {
        when (it) {
            "gzip" -> NbtCompression.Gzip
            "zlib" -> NbtCompression.Zlib
            "none" -> NbtCompression.None
            else -> error("Unknown compression kind: $it")
        }
    }.default(None)
    private val compressionLevel by option(
        "--compression-level", "-z", help = "Compression level"
    ).int().restrictTo(1, 9)

    private val pretty by option("--pretty", "-p", help = "Pretty output").flag()

    context(_: Raise<MCTError>)
    override suspend fun App() {
        enforceNot(outputFormat == Auto && output == null) {
            "Cannot infer the output format when missing the output path"
        }

        enforceNot((regex && output != null)) {
            "Not allow to specify the output when using `--regex`"
        }

        if (!regex) convert(input.toPath()) else {
            val regex = input.toRegex2()
            coroutineScope {
                fs.listRecursively(Path.CURRENT_PATH)
                    .filter { fs.metadata(it).isRegularFile && regex.matches(it.toString()) }
                    .forEach {
                        launch(Dispatchers.IO) {
                            convert(it)
                        }
                    }
            }
        }

    }

    private fun convert(inputPath: Path) {
        nbtCompression = compression
        nbtCompressionLevel = compressionLevel
        prettyOutput = pretty

        val inputFormat = inputFormat.inferAuto(inputPath)
        val outputFormat = if (outputFormat == Auto) outputFormat.inferAuto(output!!) else outputFormat

        val ir = fs.read(inputPath) {
            inputFormat.decodeToIR(this)
        }

        fs.write(output ?: inferOutput(inputPath, outputFormat)) {
            outputFormat.encodeFromIR(this, ir)
        }
    }
}

context(_: FSHolder)
private fun inferOutput(input: Path, format: ConvertableFormat): Path {
    check(format != Auto)
    val filename = input.stem
    return "$filename.${format.display}".toPath()
}