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
import mct.util.size
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
        override val message = reason.message ?: "<null>"
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
        val patches = diff(target.toList(), source.toList()).deltas.mapNotNull {
            when (it.type) {
                DeltaType.CHANGE -> PatchPreprocessingOperation.TextPatch(
                    it.source.range(),
                    it.target.lines.concatToString()
                )

                DeltaType.DELETE -> PatchPreprocessingOperation.TextPatch(it.source.range(), "")
                DeltaType.INSERT -> PatchPreprocessingOperation.TextPatch(
                    it.source.range(),
                    it.target.lines.concatToString()
                )

                DeltaType.EQUAL -> null
            }
        }
        PatchPreprocessingOperation.PatchTextFile(manifest.target, patches)
    }

    is PatchBinaryFile -> {
        ensureExist(manifest.source)
        ensureExist(manifest.target)
        val source = manifest.source.readBytes().chunk(CHUNK_SIZE)
        val target = manifest.target.readBytes().chunk(CHUNK_SIZE)
        val latestTargetChunkSize = target.lastOrNull()?.value?.size ?: 0
        val patches = diff(target, source).deltas.mapNotNull { delta ->
            val chunkRange = delta.source.range()
            val endsWithNonFullChunk = chunkRange.last == target.lastIndex
            val fullChunkCount = chunkRange.size - if (endsWithNonFullChunk) 1 else 0
            val indicesSize = if (chunkRange.isEmpty()) 0 else
                fullChunkCount * CHUNK_SIZE + if (endsWithNonFullChunk) latestTargetChunkSize else 0
            val beginIndex = target.take(chunkRange.first).sumOf { it.value.size }
            val indices = beginIndex..<(beginIndex + indicesSize)
            when (delta.type) {
                DeltaType.CHANGE -> PatchPreprocessingOperation.BinaryPatch(indices, delta.target.lines.flatten())
                DeltaType.DELETE -> PatchPreprocessingOperation.BinaryPatch(indices, EMPTY_BYTES)
                DeltaType.INSERT -> PatchPreprocessingOperation.BinaryPatch(indices, delta.target.lines.flatten())
                DeltaType.EQUAL -> null
            }
        }
        PatchPreprocessingOperation.PatchBinaryFile(manifest.target, patches)
    }
}

private const val CHUNK_SIZE = 1024
private val EMPTY_BYTES = ByteArray(0)

private class ByteArrayWrapper(
    val value: ByteArray
) {
    override fun equals(other: Any?): Boolean = other is ByteArrayWrapper && other.value.contentEquals(value)
    override fun hashCode(): Int = value.contentHashCode()
}

private fun ByteArray.chunk(atMost: Int): List<ByteArrayWrapper> {
    val bytes = this
    val latest = size % atMost
    val chunkCount = size divCeil atMost
    return Array(chunkCount) { i ->
        val chunkSize = if (i != chunkCount - 1) atMost else if (latest > 0) latest else atMost
        val beginIndex = i * atMost
        bytes.copyOfRange(beginIndex, beginIndex + chunkSize)
    }.map(::ByteArrayWrapper)
}

private inline fun List<ByteArrayWrapper>.flatten(): ByteArray {
    ifEmpty { return EMPTY_BYTES }
    var size = 0
    forEach { size += it.value.size }
    val result = ByteArray(size)
    var i = 0
    forEach {
        it.value.copyInto(result, i)
        i += it.value.size
    }
    return result
}

private inline fun <T> Chunk<T>.range() = position..last()
private fun List<Char>.concatToString(): String = toCharArray().concatToString()