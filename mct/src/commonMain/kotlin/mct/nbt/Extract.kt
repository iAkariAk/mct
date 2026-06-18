package mct.nbt

import mct.FormatKind
import mct.pointer.DataPointer
import mct.pointer.markArray
import mct.pointer.markMap
import mct.text.isTextCompound
import mct.text.isTextCompoundShorthanded
import mct.util.toSnbt
import net.benwoodworth.knbt.NbtCompound
import net.benwoodworth.knbt.NbtList
import net.benwoodworth.knbt.NbtString
import net.benwoodworth.knbt.NbtTag


internal data class PointerWithExtension(
    val pointer: DataPointer,
    val content: String,
    val kind: FormatKind,
    val type: Type = Type.Text,
) {
    enum class Type {
        Command, Text
    }
}

internal fun NbtTag.extractTexts(): Sequence<PointerWithExtension> = when (this) {
    is NbtList<*> -> asSequence().withIndex().flatMap { (index, tag) ->
        tag.extractTexts().map {
            it.copy(pointer = it.pointer.markArray(index))
        }
    } // wrap inner pointer

    is NbtCompound -> {
        if (isTextCompound()) {
            sequenceOf(PointerWithExtension(DataPointer.Terminator, toSnbt(), FormatKind.Nbt))
        } else if (isTextCompoundShorthanded()) {
            val map = toMutableMap()
            val text = map.remove("")
            map["text"] = text!!
            val expanded = NbtCompound(map)

            sequenceOf(PointerWithExtension(DataPointer.Terminator, expanded.toSnbt(), FormatKind.Nbt))
        } else {
            asSequence().flatMap { (key, value) ->
                if (key == "Command" && value is NbtString) {
                    val pwe = PointerWithExtension(
                        DataPointer.Map("Command", DataPointer.Terminator),
                        value.value,
                        FormatKind.PlainStr,
                        PointerWithExtension.Type.Command
                    )
                    return@flatMap sequenceOf(pwe)
                }
                value.extractTexts().map {
                    it.copy(pointer = it.pointer.markMap(key))
                }
            } // wrap inner pointer
        }
    }

    is NbtString -> {
        sequenceOf(PointerWithExtension(DataPointer.Terminator, value, FormatKind.PlainStr))
    }

    else -> emptySequence()
}
