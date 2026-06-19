package mct.dp.nbt

import arrow.core.raise.context.raise
import kotlinx.serialization.SerializationException
import mct.MCTPattern
import mct.dp.Extractor
import mct.dp.NbtExtractError
import mct.nbt.extractTexts
import mct.serializer.NbtNone
import net.benwoodworth.knbt.NbtTag
import net.benwoodworth.knbt.decodeFromSource

internal fun NbtExtractor(pattern: MCTPattern) = Extractor("Nbt", ".nbt") { env, sourcePath, zfs, zpath ->
    try {
        val nbt = zfs.read(zpath) { NbtNone.decodeFromSource<NbtTag>(this) }
        nbt.extractTexts()
        TODO()
    } catch (e: SerializationException) {
        raise(NbtExtractError.NbtDecodeError(sourcePath.name, zpath.toString(), e))
    }

}
