@file:OptIn(ExperimentalWasmJsInterop::class)
@file:Suppress("unused")

package mct.util

import js.regexp.RegExp
import js.regexp.RegExpExecArray
import org.intellij.lang.annotations.Language

actual typealias Destructured = MatchResult.Destructured

actual class MatchGroup2 actual constructor(
    actual val value: String,
    actual val range: IntRange,
)

actual interface MatchGroupCollection2 : Collection<MatchGroup2?> {
    actual operator fun get(index: Int): MatchGroup2?
    actual operator fun get(name: String): MatchGroup2?
}

actual interface MatchResult2 : MatchResult

internal interface HasGroups2 {
    val groups2: MatchGroupCollection2
}

actual val MatchResult2.groups2: MatchGroupCollection2
    get() = (this as HasGroups2).groups2

actual class Regex2 actual constructor(
    @Language("RegExp") pattern: String,
    actual val options: Set<RegexOption>,
) {
    private val flags = buildFlags(options)
    private val namedGroupIndices = namedGroupIndices(pattern)

    actual val pattern: String = pattern

    actual constructor(@Language("RegExp") pattern: String) : this(pattern, emptySet())
    actual constructor(@Language("RegExp") pattern: String, option: RegexOption) : this(pattern, setOf(option))

    actual infix fun matches(input: CharSequence): Boolean {
        val str = input.toString()
        val m = newRegExp().exec(str) ?: return false
        return m.index == 0 && matchValue(m, 0)?.length == str.length
    }

    actual fun containsMatchIn(input: CharSequence): Boolean {
        return newRegExp().test(input.toString())
    }

    actual fun find(input: CharSequence, startIndex: Int): MatchResult2? {
        val regex = newRegExp()
        regex.lastIndex = startIndex
        val str = input.toString()
        val m = regex.exec(str) ?: return null
        return matchResultFrom(m, pattern, flags, str, namedGroupIndices)
    }

    actual fun findAll(input: CharSequence, startIndex: Int): Sequence<MatchResult2> = sequence {
        val regex = newRegExp()
        regex.lastIndex = startIndex
        val str = input.toString()
        while (true) {
            val m = regex.exec(str) ?: break
            yield(matchResultFrom(m, pattern, flags, str, namedGroupIndices))
            advanceAfterEmptyMatch(regex, m)
        }
    }

    actual fun matchEntire(input: CharSequence): MatchResult2? {
        val str = input.toString()
        val m = newRegExp().exec(str) ?: return null
        val whole = matchValue(m, 0) ?: return null
        return if (m.index == 0 && whole.length == str.length) {
            matchResultFrom(m, pattern, flags, str, namedGroupIndices)
        } else {
            null
        }
    }

    actual fun matchAt(input: CharSequence, index: Int): MatchResult2? {
        val regex = newRegExp()
        regex.lastIndex = index
        val str = input.toString()
        val m = regex.exec(str) ?: return null
        return if (m.index == index) matchResultFrom(m, pattern, flags, str, namedGroupIndices) else null
    }

    actual fun matchesAt(input: CharSequence, index: Int): Boolean = matchAt(input, index) != null

    actual fun replace(input: CharSequence, replacement: String): String {
        return replaceByRegExp(input.toString(), newRegExp(), replacement.jsEscape())
    }

    actual fun replace(input: CharSequence, transform: (MatchResult) -> CharSequence): String {
        val str = input.toString()
        val regex = newRegExp()
        val sb = StringBuilder()
        var lastEnd = 0
        while (true) {
            val m = regex.exec(str) ?: break
            val value = matchValue(m, 0) ?: ""
            sb.append(str, lastEnd, m.index)
            sb.append(transform(matchResultFrom(m, pattern, flags, str, namedGroupIndices)))
            lastEnd = m.index + value.length
            advanceAfterEmptyMatch(regex, m)
        }
        sb.append(str.substring(lastEnd))
        return sb.toString()
    }

    actual fun replaceFirst(input: CharSequence, replacement: String): String {
        val single = RegExp(pattern, nonGlobalFlags(options))
        return replaceByRegExp(input.toString(), single, replacement.jsEscape())
    }

    actual fun split(input: CharSequence, limit: Int): List<String> {
        val str = input.toString()
        val regex = newRegExp()
        val result = mutableListOf<String>()
        val maxSplits = if (limit <= 0) Int.MAX_VALUE else limit - 1
        var lastEnd = 0
        while (result.size < maxSplits) {
            val m = regex.exec(str) ?: break
            val mIndex = m.index
            if (mIndex >= str.length) break
            val mValue = matchValue(m, 0) ?: ""
            result.add(str.substring(lastEnd, mIndex))
            lastEnd = mIndex + mValue.length
            // prevent infinite loop on zero-length matches
            if (mValue.isEmpty()) {
                if (regex.lastIndex <= lastEnd) regex.lastIndex = lastEnd + 1
            }
        }
        result.add(str.substring(lastEnd))
        return result
    }

    actual fun splitToSequence(input: CharSequence, limit: Int): Sequence<String> =
        split(input, limit).asSequence()

    actual override fun toString(): String = pattern

    private fun newRegExp(): RegExp = RegExp(pattern, flags)

    actual companion object {
        actual fun fromLiteral(literal: String): Regex2 = Regex2(literal.jsRegexEscape(), emptySet())
        actual fun escape(literal: String): String = Regex.escape(literal)
        actual fun escapeReplacement(literal: String): String = Regex.escapeReplacement(literal)
    }
}


// ---------------------------------------------------------------------------
// helpers
// ---------------------------------------------------------------------------

private fun buildFlags(options: Set<RegexOption>): String {
    val sb = StringBuilder("gd")
    if (RegexOption.IGNORE_CASE in options) sb.append("i")
    if (RegexOption.MULTILINE in options) sb.append("m")
    return sb.toString()
}

private fun String.jsEscape(): String = buildString {
    for (c in this@jsEscape) {
        if (c == '$') {
            append('$'); append('$')
        } else append(c)
    }
}

private fun String.jsRegexEscape(): String = buildString {
    for (c in this) {
        if (c in "\\^$\\.+*?()[]{}|") append('\\').append(c) else append(c)
    }
}

private fun nonGlobalFlags(options: Set<RegexOption>): String {
    val base = buildFlags(options)
    return base.replace("g", "").let { if ("d" !in it) "d$it" else it }
}

private fun matchResultFrom(
    match: RegExpExecArray,
    pattern: String,
    flags: String,
    input: String,
    namedGroupIndices: Map<String, Int>,
): MatchResult2 {
    val value = matchValue(match, 0) ?: ""
    val start: Int = match.index
    val groups = groupCollectionFrom(match)
    val groups2 = groups2From(match, namedGroupIndices)
    return object : MatchResult2, HasGroups2 {
        override val range: IntRange = IntRange(start, start + value.length - 1)
        override val value: String = value
        override val groups: MatchGroupCollection = groups
        override val groupValues: List<String> =
            (0 until matchLength(match)).map { i -> matchValue(match, i) ?: "" }

        override fun next(): MatchResult? {
            val regex = RegExp(pattern, flags)
            regex.lastIndex = nextMatchIndex(match)
            val m = regex.exec(input) ?: return null
            return matchResultFrom(m, pattern, flags, input, namedGroupIndices)
        }

        override val groups2: MatchGroupCollection2 = groups2
    }
}

private fun groupCollectionFrom(match: RegExpExecArray): MatchGroupCollection {
    val groups = mutableListOf<MatchGroup?>()
    val len = matchLength(match)
    for (i in 0 until len) {
        val value = matchValue(match, i)
        groups.add(value?.let { MatchGroup(it, matchRange(match, i)) })
    }
    val snapshot = groups.toList()
    return object : MatchGroupCollection {
        override val size: Int get() = snapshot.size
        override fun get(index: Int): MatchGroup? = snapshot.getOrNull(index)
        override fun isEmpty(): Boolean = snapshot.isEmpty()
        override fun contains(element: MatchGroup?): Boolean = snapshot.contains(element)
        override fun containsAll(elements: Collection<MatchGroup?>): Boolean = snapshot.containsAll(elements)
        override fun iterator(): Iterator<MatchGroup?> = snapshot.iterator()
    }
}

private fun groups2From(match: RegExpExecArray, namedGroupIndices: Map<String, Int>): MatchGroupCollection2 {
    val items = mutableListOf<MatchGroup2?>()
    val len = matchLength(match)
    for (i in 0 until len) {
        val v = matchValue(match, i)
        if (v == null) {
            items.add(null)
            continue
        }
        items.add(MatchGroup2(v, matchRange(match, i)))
    }
    return object : MatchGroupCollection2, AbstractCollection<MatchGroup2?>() {
        override val size: Int get() = items.size
        override fun get(index: Int): MatchGroup2? = items.getOrNull(index)
        override fun get(name: String): MatchGroup2? {
            val index = namedGroupIndices[name]
                ?: throw IllegalArgumentException("No group with name <$name>")
            return get(index)
        }

        override fun iterator(): Iterator<MatchGroup2?> = items.iterator()
    }
}

private fun matchLength(match: RegExpExecArray): Int =
    js("match.length")

private fun matchValue(match: RegExpExecArray, index: Int): String? =
    js("match[index] ?? null")

private fun matchRange(match: RegExpExecArray, index: Int): IntRange {
    val start = matchRangeStart(match, index)
    val endExclusive = matchRangeEndExclusive(match, index)
    return start until endExclusive
}

private fun matchRangeStart(match: RegExpExecArray, index: Int): Int =
    js("match.indices[index][0]")

private fun matchRangeEndExclusive(match: RegExpExecArray, index: Int): Int =
    js("match.indices[index][1]")

private fun advanceAfterEmptyMatch(regex: RegExp, match: RegExpExecArray) {
    if (matchValue(match, 0).isNullOrEmpty()) {
        regex.lastIndex = match.index + 1
    }
}

private fun nextMatchIndex(match: RegExpExecArray): Int {
    val value = matchValue(match, 0) ?: ""
    return match.index + value.length + if (value.isEmpty()) 1 else 0
}

private fun replaceByRegExp(input: String, regex: RegExp, replacement: String): String =
    js("input.replace(regex, replacement)")
