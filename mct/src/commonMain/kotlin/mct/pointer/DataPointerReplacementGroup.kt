package mct.pointer

import kotlin.collections.List as KList

sealed interface DataPointerReplacementGroup {
    data class Terminator(val replacement: String) : DataPointerReplacementGroup
    data class Map(val point: String, val values: KList<DataPointerReplacementGroup>) : DataPointerReplacementGroup
    data class List(val point: Int, val values: KList<DataPointerReplacementGroup>) : DataPointerReplacementGroup
}

private sealed interface Point {
    data object Terminator : Point
    data class Map(val point: String) : Point
    data class List(val point: Int) : Point
}

fun KList<DataPointerWithValue>.toReplacementGroups(): KList<DataPointerReplacementGroup> {
    return groupBy { (pointer, _) ->
        when (pointer) {
            is DataPointer.List -> Point.List(pointer.point)
            is DataPointer.Map -> Point.Map(pointer.point)
            DataPointer.Terminator -> Point.Terminator
        }
    }.map { (point, pointers) ->
        when (point) {
            is Point.List -> {
                val values = pointers.map { (pointer, replacement) ->
                    pointer as DataPointer.List
                    pointer.value to replacement
                }
                DataPointerReplacementGroup.List(
                    point.point,
                    values.toReplacementGroups()
                )
            }

            is Point.Map -> {
                val values = pointers.map { (pointer, replacement) ->
                    pointer as DataPointer.Map
                    pointer.value to replacement
                }
                DataPointerReplacementGroup.Map(
                    point.point,
                    values.toReplacementGroups()
                )
            }

            Point.Terminator -> {
                val (_, replacement) = pointers.first()
                DataPointerReplacementGroup.Terminator(replacement)
            }
        }
    }
}