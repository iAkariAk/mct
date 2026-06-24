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

internal fun NbtExtractor(pattern: MCTPattern) = Extractor("Nbt", "nbt") { sourcePath, (file, tmp) ->
    val (getSource, close) = tmp
    val source = getSource()
    try {
        val nbt = NbtGzip.decodeFromSource<NbtTag>(source)
        nbt.extractTexts(pattern).map {
            DatapackExtraction.Nbt(it)
        }.toList()
    } catch (e: SerializationException) {
        raise(NbtExtractError.NbtDecodeError(sourcePath.name, file.path, e))
    } finally {
        close(source)
    }
}
