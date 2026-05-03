package mct.gui

val Int.k get() = this / 1000.0
val Int.m get() = this / 1000000.0
val Int.g get() = this / 1000000000.0

fun Int.renderWithUnit() = when {
    this <= 1000 -> "$this"
    else -> "%.2fk".format(this.k)
}