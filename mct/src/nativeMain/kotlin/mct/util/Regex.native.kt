package mct.util

actual interface MatchResult2 : MatchResult

actual val MatchResult2.groups2: MatchGroupCollection2
    get() = NamedMatchGroupCollection2(groups, (this as? HasNamedGroupIndices)?.namedGroupIndices ?: emptyMap())

actual interface MatchGroupCollection2 : Collection<MatchGroup2?> {
    actual operator fun get(index: Int): MatchGroup2?
    actual operator fun get(name: String): MatchGroup2?
}

actual typealias MatchGroup2 = MatchGroup
actual typealias Destructured = MatchResult.Destructured

private interface HasNamedGroupIndices {
    val namedGroupIndices: Map<String, Int>
}

private class NamedMatchGroupCollection2(
    private val groups: MatchGroupCollection,
    private val namedGroupIndices: Map<String, Int>,
) : MatchGroupCollection2, Collection<MatchGroup2?> by groups {
    override fun get(index: Int): MatchGroup2? = groups[index]
    override fun get(name: String): MatchGroup2? = namedGroupIndices[name]?.let(groups::get)
        ?: throw IllegalArgumentException("No group with name <$name>")
}

actual class Regex2 actual constructor(
    pattern: String,
    actual val options: Set<RegexOption>,
) {
    private val inner: Regex = Regex(pattern, options)
    private val namedGroupIndices = namedGroupIndices(pattern)

    actual val pattern: String = pattern

    actual constructor(pattern: String) : this(pattern, emptySet())
    actual constructor(pattern: String, option: RegexOption) : this(pattern, setOf(option))

    actual infix fun matches(input: CharSequence) = inner matches input
    actual fun containsMatchIn(input: CharSequence) = inner.containsMatchIn(input)
    actual fun find(input: CharSequence, startIndex: Int) = inner.find(input, startIndex)?.withNamedGroupIndices(namedGroupIndices)
    actual fun findAll(input: CharSequence, startIndex: Int) = inner.findAll(input, startIndex).map { it.withNamedGroupIndices(namedGroupIndices) }
    actual fun matchEntire(input: CharSequence) = inner.matchEntire(input)?.withNamedGroupIndices(namedGroupIndices)
    actual fun matchAt(input: CharSequence, index: Int) = inner.matchAt(input, index)?.withNamedGroupIndices(namedGroupIndices)
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

private fun MatchResult.withNamedGroupIndices(namedGroupIndices: Map<String, Int>): MatchResult2 =
    object : MatchResult2, MatchResult by this, HasNamedGroupIndices {
        override val namedGroupIndices = namedGroupIndices

        override fun next(): MatchResult? = this@withNamedGroupIndices.next()?.withNamedGroupIndices(namedGroupIndices)
    }
