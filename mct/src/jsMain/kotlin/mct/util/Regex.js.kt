package mct.util

import kotlin.js.RegExp

actual typealias Destructured = MatchResult.Destructured

actual class MatchGroup2 actual constructor(
    actual val value: String,
    actual val range: IntRange
)

actual interface MatchGroupCollection2 : Collection<MatchGroup2?> {
    actual operator fun get(index: Int): MatchGroup2?
}

operator fun MatchGroupCollection2.get(name: String): MatchGroup2? = null

actual interface MatchResult2 : MatchResult

internal interface HasGroups2 {
    val groups2: MatchGroupCollection2
}

actual val MatchResult2.groups2: MatchGroupCollection2
    get() = (this as HasGroups2).groups2

actual class Regex2 actual constructor(
    pattern: String,
    actual val options: Set<RegexOption>
) {
    private val regExp: RegExp = RegExp(pattern, buildFlags(options))

    actual val pattern: String = pattern

    actual constructor(pattern: String) : this(pattern, emptySet())
    actual constructor(pattern: String, option: RegexOption) : this(pattern, setOf(option))

    actual infix fun matches(input: CharSequence): Boolean {
        val str = input.toString()
        regExp.lastIndex = 0
        val m = regExp.exec(str) ?: return false
        return m.index == 0 && m[0]?.length == str.length
    }

    actual fun containsMatchIn(input: CharSequence): Boolean {
        regExp.lastIndex = 0
        return regExp.test(input.toString())
    }

    actual fun find(input: CharSequence, startIndex: Int): MatchResult2? {
        regExp.lastIndex = startIndex
        val str = input.toString()
        val m = regExp.exec(str) ?: return null
        return matchResultFrom(m, regExp, str)
    }

    actual fun findAll(input: CharSequence, startIndex: Int): Sequence<MatchResult2> = sequence {
        regExp.lastIndex = startIndex
        val str = input.toString()
        while (true) {
            val m = regExp.exec(str) ?: break
            yield(matchResultFrom(m, regExp, str))
        }
    }

    actual fun matchEntire(input: CharSequence): MatchResult2? {
        val str = input.toString()
        regExp.lastIndex = 0
        val m = regExp.exec(str) ?: return null
        val whole = m[0] ?: return null
        return if (m.index == 0 && whole.length == str.length) matchResultFrom(m, regExp, str) else null
    }

    actual fun matchAt(input: CharSequence, index: Int): MatchResult2? {
        regExp.lastIndex = index
        val str = input.toString()
        val m = regExp.exec(str) ?: return null
        return if (m.index == index) matchResultFrom(m, regExp, str) else null
    }

    actual fun matchesAt(input: CharSequence, index: Int): Boolean = matchAt(input, index) != null

    actual fun replace(input: CharSequence, replacement: String): String {
        val s: dynamic = input.toString()
        return s.replace(regExp, replacement.jsEscape()) as String
    }

    actual fun replace(input: CharSequence, transform: (MatchResult) -> CharSequence): String {
        val str = input.toString()
        val sb = StringBuilder()
        var lastEnd = 0
        regExp.lastIndex = 0
        while (true) {
            val m = regExp.exec(str) ?: break
            sb.append(str, lastEnd, m.index)
            sb.append(transform(matchResultFrom(m, regExp, str)))
            lastEnd = regExp.lastIndex
        }
        sb.append(str.substring(lastEnd))
        return sb.toString()
    }

    actual fun replaceFirst(input: CharSequence, replacement: String): String {
        val s: dynamic = input.toString()
        val single = RegExp(pattern, nonGlobalFlags(options))
        return s.replace(single, replacement.jsEscape()) as String
    }

    actual fun split(input: CharSequence, limit: Int): List<String> {
        val str = input.toString()
        val result = mutableListOf<String>()
        val maxSplits = if (limit <= 0) Int.MAX_VALUE else limit - 1
        var lastEnd = 0
        regExp.lastIndex = 0
        while (result.size < maxSplits) {
            val m = regExp.exec(str) ?: break
            val mIndex = m.index
            if (mIndex >= str.length) break
            val mValue = m[0] ?: ""
            result.add(str.substring(lastEnd, mIndex))
            lastEnd = mIndex + mValue.length
            // prevent infinite loop on zero-length matches
            if (mValue.isEmpty()) {
                if (regExp.lastIndex <= lastEnd) regExp.lastIndex = lastEnd + 1
            }
        }
        result.add(str.substring(lastEnd))
        return result
    }

    actual fun splitToSequence(input: CharSequence, limit: Int): Sequence<String> =
        split(input, limit).asSequence()

    actual override fun toString(): String = pattern

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
        if (c == '$') { append('$'); append('$') } else append(c)
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

private fun matchResultFrom(match: dynamic, regex: RegExp, input: String): MatchResult2 {
    val value: String = match[0] ?: ""
    val start: Int = match.index
    val groups = groupCollectionFrom(match)
    val groups2 = groups2From(match)
    return object : MatchResult2, HasGroups2 {
        override val range: IntRange = IntRange(start, start + value.length)
        override val value: String = value
        override val groups: MatchGroupCollection = groups
        override val groupValues: List<String> =
            (0 until match.length).map { i -> (match[i] as? String) ?: "" }
        override fun next(): MatchResult? {
            val m = regex.exec(input) ?: return null
            return matchResultFrom(m, regex, input)
        }
        override val groups2: MatchGroupCollection2 = groups2
    }
}

private fun groupCollectionFrom(match: dynamic): MatchGroupCollection {
    val groups = mutableListOf<MatchGroup>()
    val len: Int = match.length
    for (i in 0 until len) {
        val v: String = (match[i] as? String) ?: continue
        groups.add(MatchGroup(v))
    }
    val snapshot = groups.toList()
    return object : MatchGroupCollection {
        override val size: Int get() = snapshot.size
        override fun get(index: Int): MatchGroup? = snapshot.getOrNull(index)
        override fun isEmpty(): Boolean = snapshot.isEmpty()
        override fun contains(element: MatchGroup?): Boolean = element != null && snapshot.contains(element)
        override fun containsAll(elements: Collection<MatchGroup?>): Boolean = elements.all { contains(it) }
        override fun iterator(): Iterator<MatchGroup?> = snapshot.map { it as MatchGroup? }.iterator()
    }
}

private fun groups2From(match: dynamic): MatchGroupCollection2 {
    val items = mutableListOf<MatchGroup2?>()
    val len: Int = match.length
    val indicesArr = match.indices as? Array<dynamic>
    for (i in 0 until len) {
        val v: String = (match[i] as? String) ?: continue
        val range = if (indicesArr != null && i < indicesArr.size) {
            val r = indicesArr[i]
            IntRange((r[0] as Int), (r[1] as Int))
        } else {
            IntRange(0, v.length)
        }
        items.add(MatchGroup2(v, range))
    }
    val nonNull = items.filterNotNull()
    return object : MatchGroupCollection2, AbstractCollection<MatchGroup2?>() {
        override val size: Int get() = nonNull.size
        override fun get(index: Int): MatchGroup2? = items.getOrNull(index)
        override fun iterator(): Iterator<MatchGroup2?> = nonNull.map { it as MatchGroup2? }.iterator()
    }
}
