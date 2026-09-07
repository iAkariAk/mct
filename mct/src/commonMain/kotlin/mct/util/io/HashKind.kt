@file:OptIn(FlowPreview::class)

package mct.util.io

import arrow.fx.coroutines.parMapNotNullUnordered
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import okio.*

enum class HashKind {
    MD5,
    SHA1,
    SHA256,
    SHA512;

    fun hashingSourceOf(source: Source): HashingSource = when (this) {
        MD5 -> HashingSource.md5(source)
        SHA1 -> HashingSource.sha1(source)
        SHA256 -> HashingSource.sha256(source)
        SHA512 -> HashingSource.sha512(source)
    }
}

fun FileSystem.computeHashTree(
    dir: Path, hash: HashKind
): Flow<Pair<Path, String>> = listRecursively(dir).asFlow().parMapNotNullUnordered(128) { file ->
    if (metadata(file).isDirectory) return@parMapNotNullUnordered null
    file to computeHash(file, hash)
}


private val BLACKHOLE_SINK = blackholeSink()
fun FileSystem.computeHash(file: Path, hash: HashKind): String {
    val hashingSource = hash.hashingSourceOf(source(file))
    hashingSource.buffer().use { source ->
        source.readAll(BLACKHOLE_SINK)
    }
    return hashingSource.hash.hex()
}