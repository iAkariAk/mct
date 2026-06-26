package mct.util

actual typealias MatchResult2 = MatchResult

actual val MatchResult2.groups2 get(): MatchGroupCollection2 = groups
actual typealias MatchGroupCollection2 = MatchGroupCollection
actual typealias MatchGroup2 = MatchGroup
actual typealias Destructured = MatchResult.Destructured

actual class Regex2 actual constructor(
    pattern: String,
    actual val options: Set<RegexOption>,
) {
    private val inner: Regex = Regex(pattern, options)

    actual val pattern: String = pattern

    actual constructor(pattern: String) : this(pattern, emptySet())
    actual constructor(pattern: String, option: RegexOption) : this(pattern, setOf(option))

    actual infix fun matches(input: CharSequence) = inner matches input
    actual fun containsMatchIn(input: CharSequence) = inner.containsMatchIn(input)
    actual fun find(input: CharSequence, startIndex: Int) = inner.find(input, startIndex)
    actual fun findAll(input: CharSequence, startIndex: Int) = inner.findAll(input, startIndex)
    actual fun matchEntire(input: CharSequence) = inner.matchEntire(input)
    actual fun matchAt(input: CharSequence, index: Int) = inner.matchAt(input, index)
    actual fun matchesAt(input: CharSequence, index: Int) = inner.matchesAt(input, index)
    actual fun replace(input: CharSequence, replacement: String) = inner.replace(input, replacement)
    actual fun replace(input: CharSequence, transform: (MatchResult) -> CharSequence) = inner.replace(input, transform)
    actual fun replaceFirst(input: CharSequence, replacement: String) = inner.replaceFirst(input, replacement)
    actual fun split(input: CharSequence, limit: Int) = inner.split(input, limit)
    actual fun splitToSequence(input: CharSequence, limit: Int) = inner.splitToSequence(input, limit)
    actual override fun toString(): String = inner.toString()

    actual companion object {
        actual fun fromLiteral(literal: String): Regex2 = Regex2(Regex.escape(literal), emptySet())
        actual fun escape(literal: String) = Regex.escape(literal)
        actual fun escapeReplacement(literal: String) = Regex.escapeReplacement(literal)
    }
}
