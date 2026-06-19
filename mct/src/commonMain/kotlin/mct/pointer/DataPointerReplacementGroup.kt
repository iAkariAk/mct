package mct.pointer

import mct.model.patch.FormatKind
import kotlin.collections.List as KList

sealed interface DataPointerReplacementGroup {
    data class Terminator(val replacement: String, val kind: FormatKind) : DataPointerReplacementGroup
    data class Map(val point: String, val values: KList<DataPointerReplacementGroup>) : DataPointerReplacementGroup
    data class List(val point: Int, val values: KList<DataPointerReplacementGroup>) : DataPointerReplacementGroup
}

private sealed interface Point {
    data object Terminator : Point
    data class Map(val point: String, val kind: FormatKind) : Point
    data class List(val point: Int, val kind: FormatKind) : Point
}

fun KList<DataPointerWithValue>.toReplacementGroups(): KList<DataPointerReplacementGroup> {
    return groupBy { (pointer, _, kind) ->
        when (pointer) {
            is DataPointer.List -> Point.List(pointer.point, kind)
            is DataPointer.Map -> Point.Map(pointer.point, kind)
            DataPointer.Terminator -> Point.Terminator
        }
    }.map { (point, pointers) ->
        when (point) {
            is Point.List -> {
                val values = pointers.map { (pointer, replacement) ->
                    pointer as DataPointer.List
                    DataPointerWithValue(pointer.value, replacement, point.kind)
                }
                DataPointerReplacementGroup.List(
                    point.point,
                    values.toReplacementGroups()
                )
            }

            is Point.Map -> {
                val values = pointers.map { (pointer, replacement) ->
                    pointer as DataPointer.Map
                    DataPointerWithValue(pointer.value, replacement, point.kind)
                }
                DataPointerReplacementGroup.Map(
                    point.point,
                    values.toReplacementGroups()
                )
            }

            Point.Terminator -> {
                val (_, replacement, kind) = pointers.first()
                DataPointerReplacementGroup.Terminator(replacement, kind)
            }
        }
    }
}