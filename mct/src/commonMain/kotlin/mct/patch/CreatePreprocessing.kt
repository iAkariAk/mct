package mct.patch

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.raise
import io.github.petertrr.diffutils.diff
import io.github.petertrr.diffutils.patch.Chunk
import io.github.petertrr.diffutils.patch.DeltaType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mct.FSHolder
import mct.MCTError
import mct.MCTWorkspace
import mct.model.patch.PatchPreprocessing
import mct.model.patch.PatchPreprocessingOperation
import mct.util.divCeil
import mct.util.io.readBytes
import mct.util.io.readJson
import mct.util.io.readText
import mct.serializer.PathSerializable as Path

@Serializable
private data class PatchPreprocessingManifest(
    val ordered: List<PatchPreprocessingOperationManifest>,
    val unordered: List<PatchPreprocessingOperationManifest>,
)

@Serializable
private sealed interface PatchPreprocessingOperationManifest {
    @Serializable
    @SerialName("add_file")
    data class AddFile(val target: Path, val source: Path) : PatchPreprocessingOperationManifest

    @Serializable
    @SerialName("remove_file")
    data class RemoveFile(val target: Path) : PatchPreprocessingOperationManifest

    @Serializable
    @SerialName("patch_text")
    data class PatchTextFile(val target: Path, val source: Path) : PatchPreprocessingOperationManifest

    @Serializable
    @SerialName("patch_binary")
    data class PatchBinaryFile(val target: Path, val source: Path) : PatchPreprocessingOperationManifest
}

sealed interface CreatePreprocessingError : MCTError {
    data class ManifestNotFound(val path: Path) : CreatePreprocessingError {
        override val message = "Expect a manifest file with path $path, but nothing was found"
    }

    data class ManifestParseFailure(val reason: Throwable) : CreatePreprocessingError {
        override val message = reason.message!!
    }

    data class FileNotFound(val path: Path) : CreatePreprocessingError {
        override val message = "File $path not found"
    }
}

context(_: Raise<CreatePreprocessingError>)
fun MCTWorkspace.createPreprocessing(preprocessingManifestDir: Path): PatchPreprocessing {
    val manifestFile = preprocessingManifestDir / "preprocessing.json"
    ensure(fs.exists(manifestFile)) {
        CreatePreprocessingError.ManifestNotFound(manifestFile)
    }
    val manifest = try {
        manifestFile.readJson<PatchPreprocessingManifest>()
    } catch (e: Throwable) {
        raise(CreatePreprocessingError.ManifestParseFailure(e))
    }
    return PatchPreprocessing(
        ordered = manifest.ordered.map { evaluatePreprocessingOperation(it) },
        unordered = manifest.unordered.map { evaluatePreprocessingOperation(it) }
    )
}

context(_: Raise<CreatePreprocessingError.FileNotFound>)
private inline fun FSHolder.ensureExist(path: Path) = ensure(fs.exists(path)) {
    CreatePreprocessingError.FileNotFound(path)
}


context(_: Raise<CreatePreprocessingError>)
private fun MCTWorkspace.evaluatePreprocessingOperation(
    manifest: PatchPreprocessingOperationManifest
): PatchPreprocessingOperation = when (manifest) {
    is AddFile -> {
        ensureExist(manifest.source)
        PatchPreprocessingOperation.AddFile(manifest.target, manifest.source.readBytes())
    }

    is RemoveFile -> {
        PatchPreprocessingOperation.RemoveFile(manifest.target)
    }

    is PatchTextFile -> {
        ensureExist(manifest.source)
        ensureExist(manifest.target)
        val source = manifest.source.readText()
        val target = manifest.target.readText()
        val patches = diff(target, source).deltas.mapNotNull {
            when (it.type) {
                DeltaType.CHANGE -> PatchPreprocessingOperation.TextPatch(it.source.range(), it.target.content())
                DeltaType.DELETE -> PatchPreprocessingOperation.TextPatch(it.source.range(), "")
                DeltaType.INSERT -> PatchPreprocessingOperation.TextPatch(it.source.range(), it.target.content())
                DeltaType.EQUAL -> null
            }
        }
        PatchPreprocessingOperation.PatchTextFile(manifest.source, patches)
    }

    is PatchBinaryFile -> {
        ensureExist(manifest.source)
        ensureExist(manifest.target)
        val source = manifest.source.readBytes().chunk(1024)
        val target = manifest.target.readBytes().chunk(1024)
        val patches = diff(target, source).deltas.mapNotNull {
            when (it.type) {
                DeltaType.CHANGE -> PatchPreprocessingOperation.BinaryPatch(
                    it.source.range(),
                    it.target.lines.flatten()
                )

                DeltaType.DELETE -> PatchPreprocessingOperation.BinaryPatch(it.source.range(), EMPTY_BYTES)
                DeltaType.INSERT -> PatchPreprocessingOperation.BinaryPatch(
                    it.source.range(),
                    it.target.lines.flatten()
                )

                DeltaType.EQUAL -> null
            }
        }
        PatchPreprocessingOperation.PatchBinaryFile(manifest.source, patches)
    }
}

private val EMPTY_BYTES = ByteArray(0)
private fun ByteArray.chunk(atMost: Int): List<ByteArray> {
    val bytes = this
    val least = size % atMost
    val chunkCount = size divCeil atMost
    val delegated = Array(chunkCount) { i ->
        bytes.copyOfRange(i, i + if (i == chunkCount) least else atMost)
    }.asList()
    return object : List<ByteArray> by delegated {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is List<*>) return false
            if (size != other.size) return false
            for (i in indices) {
                val self = this[i]
                val other = other[i] as ByteArray
                if (!self.contentEquals(other)) return false
            }
            return true
        }

        override fun hashCode(): Int {
            var result = 1
            for (element in this) {
                result = 31 * result + element.contentHashCode()
            }
            return result
        }
    }
}

private inline fun List<ByteArray>.flatten(): ByteArray {
    ifEmpty { return EMPTY_BYTES }
    var size = 0
    forEach { size += it.size }
    val result = ByteArray(size)
    var i = 0
    forEach {
        it.copyInto(result, i)
        i += it.size
    }
    return result
}

private inline fun <T> Chunk<T>.range() = position..last()
private inline fun Chunk<String>.content() = lines.joinToString("\n")