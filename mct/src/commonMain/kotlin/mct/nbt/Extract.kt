package mct.nbt

import arrow.core.raise.context.either
import mct.LoggerHolder
import mct.MCTPattern
import mct.command.extractTextFromCommand
import mct.command.parseCommands
import mct.logger
import mct.model.patch.FormatKind
import mct.model.patch.NbtExtraction
import mct.pointer.DataPointer
import mct.pointer.markArray
import mct.pointer.markMap
import mct.pointer.matches
import mct.text.isTextCompound
import mct.text.isTextCompoundShorthanded
import mct.util.toSnbt
import net.benwoodworth.knbt.NbtCompound
import net.benwoodworth.knbt.NbtList
import net.benwoodworth.knbt.NbtString
import net.benwoodworth.knbt.NbtTag

context(_: LoggerHolder)
internal fun NbtTag.extractTexts(pattern: MCTPattern): Sequence<NbtExtraction> =
    extractTextsByPointer().mapNotNull { (pointer, content, kind, type) ->
        when (type) {
            PointerWithExtension.Type.Command -> either {
                val cmds = context(logger) {
                    parseCommands(content)
                }
                NbtExtraction.Command(pointer = pointer, raw = content, locations = cmds.flatMap {
                    extractTextFromCommand(
                        it, pattern.mcfunction, pattern.mcfunctionData
                    )
                }.takeIf { it.isNotEmpty() }?.map {
                    NbtExtraction.Command.Location(
                        it.indices, it.content, it.syntax
                    )
                } ?: return@mapNotNull null)
            }.getOrNull()

            PointerWithExtension.Type.Text if pointer.matches(pattern.nbt) -> NbtExtraction.Text(
                pointer = pointer, kind = kind, content = content
            )

            else -> null
        }
    }


private data class PointerWithExtension(
    val pointer: DataPointer,
    val content: String,
    val kind: FormatKind,
    val type: Type = Type.Text,
) {
    enum class Type {
        Command, Text
    }
}

private fun NbtTag.extractTextsByPointer(): Sequence<PointerWithExtension> = when (this) {
    is NbtList<*> -> asSequence().withIndex().flatMap { (index, tag) ->
        tag.extractTextsByPointer().map {
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
                value.extractTextsByPointer().map {
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
