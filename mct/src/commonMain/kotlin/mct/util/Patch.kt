package mct.util


interface StringIndices {
    val indices: IntRange
    val content: String

    operator fun component1() = indices
    operator fun component2() = content
}

fun StringIndices(indices: IntRange, content: String): StringIndices = StringIndicesImpl(indices, content)

private data class StringIndicesImpl(
    override val indices: IntRange,
    override val content: String
) : StringIndices

fun String.patch(patches: List<StringIndices>): String {
    val original = this
    val finalSize = length + patches.sumOf { it.content.length - it.indices.size }
    val result = CharArray(finalSize)
    val patchChunks = patches.sortedBy { it.indices.first }
    var originalIndex = 0
    var newIndex = 0
    for ((indices, str) in patchChunks) {
        if (indices == original.indices) return str

        val originalChunkSize = indices.first - originalIndex
        if (originalChunkSize > 0) {
            original.toCharArray(result, newIndex, originalIndex, indices.first)
            newIndex += originalChunkSize
        }
        str.toCharArray(result, newIndex)
        newIndex += str.length
        originalIndex = indices.last + 1
    }
    if (originalIndex < original.length) {
        original.toCharArray(result, newIndex, originalIndex, original.length)
    }
    return result.concatToString()
}


interface BytesIndices {
    val indices: IntRange
    val bytes: ByteArray

    operator fun component1() = indices
    operator fun component2() = bytes
}

fun BytesIndices(indices: IntRange, content: ByteArray): BytesIndices = BytesIndicesImpl(indices, content)
private class BytesIndicesImpl(
    override val indices: IntRange,
    override val bytes: ByteArray
) : BytesIndices {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as BytesIndicesImpl

        if (indices != other.indices) return false
        if (!bytes.contentEquals(other.bytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = indices.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

fun ByteArray.patch(patches: List<BytesIndices>): ByteArray {
    val original = this
    val finalSize = size + patches.sumOf { it.bytes.size - it.indices.size }
    val result = ByteArray(finalSize)
    val patchChunks = patches.sortedBy { it.indices.first }
    var originalIndex = 0
    var newIndex = 0
    for ((indices, bytes) in patchChunks) {
        if (indices == original.indices) return bytes

        val originalChunkSize = indices.first - originalIndex
        if (originalChunkSize > 0) {
            original.copyInto(result, newIndex, originalIndex, indices.first)
            newIndex += originalChunkSize
        }
        bytes.copyInto(result, newIndex)
        newIndex += bytes.size
        originalIndex = indices.last + 1
    }
    if (originalIndex < original.size) {
        original.copyInto(result, newIndex, originalIndex, original.size)
    }
    return result
}