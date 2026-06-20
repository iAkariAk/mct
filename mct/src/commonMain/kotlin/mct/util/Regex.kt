package mct.util

import kotlinx.serialization.Serializable
import mct.serializer.Regex2Serializer

fun String.toRegex2(vararg options: RegexOption): Regex2 = Regex2(this, options.toSet())

expect class Regex2 {
    constructor(pattern: String)
    constructor(pattern: String, option: RegexOption)
    constructor(pattern: String, options: Set<RegexOption>)

    val pattern: String
    val options: Set<RegexOption>
    infix fun matches(input: CharSequence): Boolean
    fun containsMatchIn(input: CharSequence): Boolean
    fun find(input: CharSequence, startIndex: Int = 0): MatchResult2?
    fun findAll(input: CharSequence, startIndex: Int = 0): Sequence<MatchResult2>
    fun matchEntire(input: CharSequence): MatchResult2?
    fun matchAt(input: CharSequence, index: Int): MatchResult2?
    fun matchesAt(input: CharSequence, index: Int): Boolean
    fun replace(input: CharSequence, replacement: String): String
    fun replace(input: CharSequence, transform: (MatchResult) -> CharSequence): String
    fun replaceFirst(input: CharSequence, replacement: String): String
    fun split(input: CharSequence, limit: Int = 0): List<String>
    fun splitToSequence(input: CharSequence, limit: Int = 0): Sequence<String>
    override fun toString(): String

    companion object {
        fun fromLiteral(literal: String): Regex2
        fun escape(literal: String): String
        fun escapeReplacement(literal: String): String
    }
}

expect interface MatchResult2 {
    val range: IntRange
    val value: String
    val groups: MatchGroupCollection
    val groupValues: List<String>
    fun next(): MatchResult?
}

expect val MatchResult2.groups2: MatchGroupCollection2
@Suppress("CAST_NEVER_SUCCEEDS")
val MatchResult2.destructured: Destructured
    get() = (this as MatchResult).destructured as Destructured

expect interface MatchGroupCollection2 : Collection<MatchGroup2?> {
    operator fun get(index: Int): MatchGroup2?
}

expect class MatchGroup2 {
    constructor(value: String, range: IntRange)

    val value: String
    val range: IntRange
}

expect class Destructured internal constructor(match: MatchResult) {
    val match: MatchResult
    operator fun component1(): String
    operator fun component2(): String
    operator fun component3(): String
    operator fun component4(): String
    operator fun component5(): String
    operator fun component6(): String
    operator fun component7(): String
    operator fun component8(): String
    operator fun component9(): String
    operator fun component10(): String
    fun toList(): List<String>
}
