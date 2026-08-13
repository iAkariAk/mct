package mct.nbt

import mct.LoggerHolder
import mct.MCTPattern
import mct.model.patch.ExtractionContent
import mct.model.patch.FormatKind
import mct.model.patch.NbtExtraction
import mct.model.patch.inferFormatKind
import mct.model.text.isTextComponent
import mct.model.text.isTextComponentShorthanded
import mct.pointer.DataPointer
import mct.pointer.compile
import mct.pointer.markArray
import mct.pointer.markMap
import mct.util.toSnbt
import mct.util.unreachable
import net.benwoodworth.knbt.NbtCompound
import net.benwoodworth.knbt.NbtList
import net.benwoodworth.knbt.NbtString
import net.benwoodworth.knbt.NbtTag

context(_: LoggerHolder)
internal fun NbtTag.extractTexts(pattern: MCTPattern): Sequence<NbtExtraction> =
    extractTextsByPointer().mapNotNull { pwe ->
        val nbtPatterns = pattern.nbt
        if (nbtPatterns != null) pwe.pointer.compile().matched(nbtPatterns)?.let { dataPointerPattern ->
            val content = dataPointerPattern.kind.parse(pwe.content, pwe.format, pattern) ?: return@mapNotNull null
            NbtExtraction(pwe.pointer, content)
        } else NbtExtraction(pwe.pointer, ExtractionContent.Text(pwe.format, pwe.content))
    }


private data class PointerWithExtension(
    val pointer: DataPointer,
    val contentProvider: Any, // () -> String | String
    val format: FormatKind,
) {
    @Suppress("UNCHECKED_CAST")
    val content
        get() = when (contentProvider) {
            is String -> contentProvider
            else -> (contentProvider as? () -> String ?: unreachable)()
        }
}

private fun NbtTag.extractTextsByPointer(): Sequence<PointerWithExtension> = when (this) {
    is NbtList<*> -> if (isTextComponent()) {
        sequenceOf(
            PointerWithExtension(
                DataPointer.Terminator,
                { toSnbt() },
                FormatKind.Nbt,
            )
        )
    } else asSequence().withIndex().flatMap { (index, tag) ->
        tag.extractTextsByPointer().map {
            it.copy(pointer = it.pointer.markArray(index))
        }
    } // wrap inner pointer

    is NbtCompound -> if (isTextComponent()) {
        sequenceOf(PointerWithExtension(DataPointer.Terminator, { toSnbt() }, FormatKind.Nbt))
    } else if (isTextComponentShorthanded()) {
        val map = toMutableMap()
        val text = map.remove("")
        map["text"] = text!!
        val expanded = NbtCompound(map)

        sequenceOf(PointerWithExtension(DataPointer.Terminator, { expanded.toSnbt() }, FormatKind.Nbt))
    } else asSequence().flatMap { (key, value) ->
        value.extractTextsByPointer().map {
            it.copy(pointer = it.pointer.markMap(key))
        }
    } // wrap inner pointer


    is NbtString -> sequenceOf(PointerWithExtension(DataPointer.Terminator, value, value.inferFormatKind()))

    else -> emptySequence()
}
