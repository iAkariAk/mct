package mct.dp.nbt

import arrow.core.raise.context.raise
import kotlinx.serialization.SerializationException
import mct.MCTPattern
import mct.dp.Extractor
import mct.dp.NbtExtractError
import mct.model.patch.DatapackExtraction
import mct.nbt.extractTexts
import mct.serializer.NbtGzip
import net.benwoodworth.knbt.NbtTag
import net.benwoodworth.knbt.decodeFromSource

internal fun NbtExtractor(pattern: MCTPattern) = Extractor("Nbt", "nbt") { sourcePath, zfs, zpath ->
    try {
        val nbt = zfs.read(zpath) { NbtGzip.decodeFromSource<NbtTag>(this) }
        nbt.extractTexts(pattern).map {
            DatapackExtraction.Nbt(it)
        }.toList()
    } catch (e: SerializationException) {
        raise(NbtExtractError.NbtDecodeError(sourcePath.name, zpath.toString(), e))
    }
}
