package mct.nbt

import kotlinx.serialization.decodeFromString
import mct.FormatKind
import mct.LoggerHolder
import mct.isString
import mct.logger
import mct.pointer.DataPointerReplacementGroup
import mct.serializer.Snbt
import mct.text.TextCompound
import mct.text.encodeToIR
import mct.util.formatir.toNbtTag
import net.benwoodworth.knbt.NbtCompound
import net.benwoodworth.knbt.NbtList
import net.benwoodworth.knbt.NbtString
import net.benwoodworth.knbt.NbtTag

private fun List<NbtTag>.toTCListStandardized() = map {
    when (it) {
        is NbtString -> TextCompound.Plain(it.value).encodeToIR(false).toNbtTag() as NbtCompound
        is NbtCompound -> it
        else -> error("Unexpected tag type $it")
    }
}.let { NbtList(it) }

context(_: LoggerHolder)
internal fun NbtTag.transform(
    pointers: List<DataPointerReplacementGroup>,
): NbtTag? = when (this) {
    is NbtList<*> -> {
        val pointers = pointers.filterIsInstance<DataPointerReplacementGroup.List>()
            .filter { it.point < size }
        if (isEmpty()) return this // safely first to infer type
        val transformed = toMutableList()
        pointers.forEach { pointer ->
            val orig = transformed[pointer.point]
            transformed[pointer.point] = orig.transform(pointer.values) ?: orig
        }
        transformed.toTCListStandardized()
    }

    is NbtCompound -> {
        pointers.firstOrNull { it is DataPointerReplacementGroup.Terminator && it.kind == FormatKind.Nbt }
            ?.let { terminator ->
                terminator as DataPointerReplacementGroup.Terminator

                return try {
                    Snbt.decodeFromString(terminator.replacement)
                } catch (e: Throwable) {
                    logger.error {
                        "Cannot decode ${terminator.replacement} as SNBT" }
                    null
//                    throw e
                }
            }

        val pointers = pointers.filterIsInstance<DataPointerReplacementGroup.Map>()
        val transformed = toMutableMap()
        pointers.forEach { pointer ->
            transformed[pointer.point]?.let {
                transformed[pointer.point] = it.transform(pointer.values) ?: it
            }
        }
        NbtCompound(transformed)
    }

    is NbtString -> {
        val pointer =
            pointers.firstOrNull { it is DataPointerReplacementGroup.Terminator && it.kind.isString() } ?: return this
        pointer as DataPointerReplacementGroup.Terminator
        NbtString(pointer.replacement)
    }

    else -> this
}

