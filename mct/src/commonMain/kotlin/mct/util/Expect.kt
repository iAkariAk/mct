package mct.util

data class NotMatchedItem<T>(
    val expected: T,
    val actual: T,
)