package mct.region

import arrow.core.raise.Raise
import arrow.core.raise.context.raise
import arrow.core.raise.recover
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import mct.MCTWorkspace
import mct.RegionReplacementGroup
import mct.pointer.DataPointer
import mct.pointer.DataPointerReplacementGroup
import mct.pointer.toReplacementGroups
import mct.region.anvil.model.ChunkDataKind
import mct.serializer.Snbt
import net.benwoodworth.knbt.*

context(_: Raise<BackfillError>)
suspend fun MCTWorkspace.backfillRegion(replacementGroups: Iterable<RegionReplacementGroup>) = coroutineScope {
    replacementGroups.asFlow().collect { group ->
        val dimension = dimensions[group.dimension]
            ?: raise(BackfillError.DimensionNotFound(group.dimension))
        val mgr = when (group.kind) {
            ChunkDataKind.Terrain -> dimension.regionRawMgr
            ChunkDataKind.Entities -> dimension.entitiesRawMgr
            ChunkDataKind.Poi -> dimension.poiRawMgr
        }
        if (mgr == null) return@collect
        launch {
            recover({
                launch {
                    mgr.modify(group.coord) { region ->
                        val chunks = region.chunks.toMutableList()
                        group.replacements.groupBy { it.index }
                            .forEach { (index, replacements) ->
                                val replacementGroups =
                                    replacements.map { it.pointer to it.replacement }.toReplacementGroups()
                                val chunk = chunks[index] ?: return@forEach
                                chunks[index] = chunk.modify { it.transform(replacementGroups) }
                            }
                        region.modifyChunks(chunks)
                    }
                }
            }, {
                raise(BackfillError.Internal(it))
            })
        }
    }
}


private fun NbtTag.transform(pointer: DataPointer, replacement: String): NbtTag = when (pointer) {
    is DataPointer.List -> {
        if (this !is NbtList<*>) return this
        if (size <= pointer.point) return this
        if (isEmpty()) return this // safely first to infer type
        val transformed = toMutableList()
        transformed[pointer.point] = transformed[pointer.point].transform(pointer.value, replacement)
        transformed.toNbtListUnsafe()
    }

    is DataPointer.Map -> {
        if (this !is NbtCompound) return this
        if (!containsKey(pointer.point)) return this
        val transformed = toMutableMap()
        transformed[pointer.point] = transformed[pointer.point]!!.transform(pointer.value, replacement)
        NbtCompound(transformed)
    }

    DataPointer.Terminator -> {
        return Snbt.decodeFromString<NbtTag>(replacement)
    }
}


private fun NbtTag.transform(
    pointers: List<DataPointerReplacementGroup>,
): NbtTag = when (this) {
    is NbtList<*> -> {
        val pointers = pointers.filterIsInstance<DataPointerReplacementGroup.List>()
            .filter { it.point < size }
        if (isEmpty()) return this // safely first to infer type
        val transformed = toMutableList()
        pointers.forEach { pointer ->
            transformed[pointer.point] = transformed[pointer.point].transform(pointer.values)
        }
        transformed.toNbtListUnsafe()
    }

    is NbtCompound -> {
        val pointers = pointers.filterIsInstance<DataPointerReplacementGroup.Map>()

        val transformed = toMutableMap()
        pointers.forEach { pointer ->
            transformed[pointer.point]?.let {
                transformed[pointer.point] = it.transform(pointer.values)
            }
        }
        NbtCompound(transformed)
    }

    is NbtString -> {
        val pointers = pointers.filterIsInstance<DataPointerReplacementGroup.Terminator>()

        val pointer = pointers.firstOrNull() ?: return this

        Snbt.decodeFromString<NbtTag>(pointer.replacement)
    }

    else -> this
}


private fun List<NbtTag>.toNbtListUnsafe(): NbtList<NbtTag> {
    val first = first()

    @Suppress(
        "CAST_NEVER_SUCCEEDS", "UNCHECKED_CAST",
        "UPPER_BOUND_VIOLATED_IN_TYPE_OPERATOR_OR_PARAMETER_BOUNDS_WARNING"
    )
    fun <T> cast(): List<T> = this as List<T>
    return when (first) {
        is NbtByte -> NbtList(cast<NbtByte>())
        is NbtByteArray -> NbtList(cast<NbtByteArray>())
        is NbtCompound -> NbtList(cast<NbtCompound>())
        is NbtDouble -> NbtList(cast<NbtDouble>())
        is NbtFloat -> NbtList(cast<NbtFloat>())
        is NbtInt -> NbtList(cast<NbtInt>())
        is NbtIntArray -> NbtList(cast<NbtIntArray>())
        is NbtLong -> NbtList(cast<NbtLong>())
        is NbtLongArray -> NbtList(cast<NbtLongArray>())
        is NbtShort -> NbtList(cast<NbtShort>())
        is NbtString -> NbtList(cast<NbtString>())
        is NbtList<*> -> NbtList(cast<NbtList<*>>())
    }
}
