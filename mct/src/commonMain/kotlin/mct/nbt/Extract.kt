package mct.nbt

import mct.LoggerHolder
import mct.MCTPattern
import mct.command.extractTextFromCommands
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
        val (pointer, _, format, type) = pwe
        when (type) {
            Command -> {
                val content = pwe.content
                NbtExtraction.Command(
                    pointer = pointer,
                    raw = content,
                    locations = extractTextFromCommands(
                        commandStr = content,
                        patterns = pattern
                    ).takeIf { it.isNotEmpty() }?.map {
                        NbtExtraction.Command.Location(it.indices, it.content, it.syntax, it.format)
                    } ?: return@mapNotNull null)
            }

            Text if pointer.compile().matches(pattern.nbt) -> NbtExtraction.Text(pointer, format, pwe.content)

            else -> null
        }
    }


private data class PointerWithExtension(
    val pointer: DataPointer,
    val contentProvider: Any, // () -> String | String
    val format: FormatKind,
    val type: Type = Text,
) {
    @Suppress("UNCHECKED_CAST")
    val content
        get() = when (contentProvider) {
            is String -> contentProvider
            else -> (contentProvider as? () -> String ?: unreachable)()
        }

    enum class Type {
        Command, Text
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
        if (key == "Command" && value is NbtString) {
            val pwe = PointerWithExtension(
                DataPointer.Map("Command", DataPointer.Terminator),
                value.value,
                FormatKind.PlainStr,
                Command
            )
            return@flatMap sequenceOf(pwe)
        }
        value.extractTextsByPointer().map {
            it.copy(pointer = it.pointer.markMap(key))
        }
    } // wrap inner pointer


    is NbtString -> sequenceOf(PointerWithExtension(DataPointer.Terminator, value, value.inferFormatKind()))

    else -> emptySequence()
}
