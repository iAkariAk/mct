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
    /**
     * The caller's pattern, reported verbatim by [toString].
     */
    actual val pattern: String = pattern

    /**
     * [pattern] after `LITERAL`/`COMMENTS` translation: what actually gets compiled.
     *
     * The JVM hands these options to its own engine, JS has no equivalent, so they are applied to the
     * text before it reaches `RegExp`.
     */
    private val compiledPattern = translatePattern(pattern, options)
    private val flags = buildFlags(compiledPattern, options)
    private val nonGlobalFlagString = nonGlobalFlags(compiledPattern, options)

    /**
     * Whole-input match: `^(?:p)(?![\s\S])`.
     *
     * JS has no whole-input matcher, and `exec` returns the leftmost alternative (`a|ab` against `ab`
     * gives `a`). The leading `^` plus the `(?![\s\S])` end-of-input assertion makes the engine
     * backtrack until the entire string is consumed, which is what the JVM's `matches` requires. A
     * trailing `$` would not do: under `MULTILINE` it also matches before a line terminator, so a
     * shorter alternative would win where the JVM would have backtracked further.
     */
    private val anchoredPattern = "^(?:" + compiledPattern + ")(?![\\s\\S])"

    private val namedGroupIndices = namedGroupIndices(compiledPattern)

    // Cached regexes: `matches`/`find`/… sit in the extraction hot loop and rebuilding a `RegExp`
    // recompiles the pattern every time. The iterating members (`findAll`, `split`,
    // `replace(transform)`) keep a private instance instead, because they hold `lastIndex` across a
    // lazy sequence that a caller can pause and resume.
    private val singleRegex: RegExp by lazy { RegExp(compiledPattern, flags) }
    private val singleNonGlobalRegex: RegExp by lazy { RegExp(compiledPattern, nonGlobalFlagString) }
    private val anchoredRegex: RegExp by lazy { RegExp(anchoredPattern, flags) }

    actual constructor(@Language("RegExp") pattern: String) : this(pattern, emptySet())
    actual constructor(@Language("RegExp") pattern: String, option: RegexOption) : this(pattern, setOf(option))

    actual infix fun matches(input: CharSequence): Boolean {
        val str = input.toString()
        val m = anchoredRegex.withLastIndex(0).exec(str) ?: return false
        // `^` under `MULTILINE` can also match at a later line start; only index 0 is a whole match.
        return m.index == 0
    }

    actual fun containsMatchIn(input: CharSequence): Boolean =
        singleRegex.withLastIndex(0).test(input.toString())

    actual fun find(input: CharSequence, startIndex: Int): MatchResult2? {
        val str = input.toString()
        val m = singleRegex.withLastIndex(startIndex).exec(str) ?: return null
        return matchResultFrom(m, compiledPattern, flags, str, namedGroupIndices)
    }

    actual fun findAll(input: CharSequence, startIndex: Int): Sequence<MatchResult2> = sequence {
        val regex = RegExp(compiledPattern, flags)
        regex.lastIndex = startIndex
        val str = input.toString()
        while (true) {
            val m = regex.exec(str) ?: break
            yield(matchResultFrom(m, compiledPattern, flags, str, namedGroupIndices))
            advanceAfterEmptyMatch(regex, m)
        }
    }

    actual fun matchEntire(input: CharSequence): MatchResult2? {
        val str = input.toString()
        val m = anchoredRegex.withLastIndex(0).exec(str) ?: return null
        if (m.index != 0) return null
        return matchResultFrom(m, compiledPattern, flags, str, namedGroupIndices)
    }

    actual fun matchAt(input: CharSequence, index: Int): MatchResult2? {
        val str = input.toString()
        val m = singleRegex.withLastIndex(index).exec(str) ?: return null
        return if (m.index == index) matchResultFrom(m, compiledPattern, flags, str, namedGroupIndices) else null
    }

    actual fun matchesAt(input: CharSequence, index: Int): Boolean = matchAt(input, index) != null

    /**
     * Replace every match, expanding [replacement] the way the JVM does.
     *
     * The expansion is done here rather than by `String.replace`: JS reads `$&`/`` $` `` and treats a
     * lone `$` as a literal, while the JVM reads `$1`/`${name}` and rejects anything else. Sharing
     * the JVM's parser is what keeps the two targets producing the same text.
     */
    actual fun replace(input: CharSequence, replacement: String): String =
        replaceAll(input.toString()) { expandReplacement(replacement, it) }

    actual fun replace(input: CharSequence, transform: (MatchResult) -> CharSequence): String =
        replaceAll(input.toString()) { transform(it) }

    actual fun replaceFirst(input: CharSequence, replacement: String): String {
        val str = input.toString()
        val m = singleNonGlobalRegex.withLastIndex(0).exec(str) ?: return str
        val matched = matchResultFrom(m, compiledPattern, flags, str, namedGroupIndices)
        return str.substring(0, m.index) +
            expandReplacement(replacement, matched) +
            str.substring(m.index + (matchValue(m, 0)?.length ?: 0))
    }

    actual fun split(input: CharSequence, limit: Int): List<String> {
        val str = input.toString()
        val regex = RegExp(compiledPattern, flags)
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
        // `limit == 0` means "no limit", and Java drops the trailing empty strings in that case.
        if (limit == 0) while (result.isNotEmpty() && result.last().isEmpty()) result.removeAt(result.size - 1)
        return result
    }

    actual fun splitToSequence(input: CharSequence, limit: Int): Sequence<String> =
        split(input, limit).asSequence()

    actual override fun toString(): String = pattern

    private fun replaceAll(
        str: String,
        transform: (MatchResult2) -> CharSequence,
    ): String {
        val regex = RegExp(compiledPattern, flags)
        val sb = StringBuilder()
        var lastEnd = 0
        while (true) {
            val m = regex.exec(str) ?: break
            val value = matchValue(m, 0) ?: ""
            sb.append(str, lastEnd, m.index)
            sb.append(transform(matchResultFrom(m, compiledPattern, flags, str, namedGroupIndices)))
            lastEnd = m.index + value.length
            advanceAfterEmptyMatch(regex, m)
        }
        sb.append(str.substring(lastEnd))
        return sb.toString()
    }

    actual companion object {
        actual fun fromLiteral(literal: String): Regex2 = Regex2(literal.jsRegexEscape(), emptySet())
        actual fun escape(literal: String): String = Regex.escape(literal)
        actual fun escapeReplacement(literal: String): String = Regex.escapeReplacement(literal)
    }
}


// ---------------------------------------------------------------------------
// helpers
// ---------------------------------------------------------------------------

private fun buildFlags(pattern: String, options: Set<RegexOption>): String {
    val sb = StringBuilder("gd")
    if (RegexOption.IGNORE_CASE in options) sb.append("i")
    if (RegexOption.MULTILINE in options) sb.append("m")
    if (RegexOption.DOT_MATCHES_ALL in options) sb.append("s")
    // `\p{…}`/`\P{…}` are Unicode property escapes only in `u` mode; without it JS turns the class
    // into a literal character set, so a pattern like `[^\p{L}\p{M}]` would reject CJK where the JVM
    // accepts it. `u` also makes constructs the JVM tolerates (a lone `]`, for one) a syntax error,
    // so it is only switched on when the pattern needs it.
    if (needsUnicodeMode(pattern)) sb.append("u")
    return sb.toString()
}

/**
 * Translate the options JS's engine cannot express into the pattern text.
 *
 * `LITERAL` quotes the whole pattern; `COMMENTS` strips whitespace and `#` comments; `UNIX_LINES`
 * makes `.` stop at `\n` alone (JS's `.` already refuses to match any line terminator, and the JVM
 * with `UNIX_LINES` only excludes `\n`). The one thing left to the JS engine is `^`/`$` under
 * `MULTILINE`: JS breaks lines on `\r`, `\u2028` and `\u2029` too, where the JVM with `UNIX_LINES`
 * breaks on `\n` only.
 */
private fun translatePattern(pattern: String, options: Set<RegexOption>): String {
    if (RegexOption.LITERAL in options) return pattern.jsRegexEscape()
    var result = pattern
    if (RegexOption.COMMENTS in options) result = result.stripPatternComments()
    if (RegexOption.UNIX_LINES in options) result = result.withUnixLines()
    return result
}

private fun needsUnicodeMode(pattern: String): Boolean =
    pattern.contains("\\p{") || pattern.contains("\\P{")

/** Drop whitespace and `#`-to-end-of-line comments, as the JVM's `COMMENTS` mode does. */
private fun String.stripPatternComments(): String = buildString {
    var inClass = false
    var i = 0
    while (i < length) {
        val c = this@stripPatternComments[i]
        when {
            c == '\\' && i + 1 < length -> {
                append(c); append(this@stripPatternComments[i + 1]); i += 2
            }

            c == '[' -> {
                inClass = true; append(c); i++
            }

            c == ']' -> {
                inClass = false; append(c); i++
            }

            !inClass && c == '#' -> while (i < length && this@stripPatternComments[i] != '\n') i++
            !inClass && c.isWhitespace() -> i++
            else -> {
                append(c); i++
            }
        }
    }
}

/** `UNIX_LINES`: a dot matches everything but `\n`, where JS's default dot excludes more. */
private fun String.withUnixLines(): String = buildString {
    var inClass = false
    var i = 0
    while (i < length) {
        val c = this@withUnixLines[i]
        when {
            c == '\\' && i + 1 < length -> {
                append(c); append(this@withUnixLines[i + 1]); i += 2
            }

            c == '[' -> {
                inClass = true; append(c); i++
            }

            c == ']' -> {
                inClass = false; append(c); i++
            }

            !inClass && c == '.' -> {
                append("[^\\n]"); i++
            }

            else -> {
                append(c); i++
            }
        }
    }
}

private fun String.jsRegexEscape(): String = buildString {
    for (c in this) {
        if (c in "\\^$\\.+*?()[]{}|") append('\\').append(c) else append(c)
    }
}

private fun nonGlobalFlags(pattern: String, options: Set<RegexOption>): String =
    buildFlags(pattern, options).replace("g", "")

private fun RegExp.withLastIndex(index: Int): RegExp = apply { lastIndex = index }

/**
 * Expand a replacement string the way the JVM's `Matcher.appendReplacement` does.
 *
 * `$1`/`$0` select a numbered group, `${name}` a named one, `\x` is the literal `x`, and anything
 * else after `$` is an error — the same contract `Regex.replace` documents on the JVM. A group that
 * did not participate in the match expands to nothing, and a numbered reference past the last group
 * is an `IndexOutOfBoundsException`, both as on the JVM.
 */
private fun expandReplacement(replacement: String, match: MatchResult2): String = buildString {
    val groups = match.groups
    val lastGroup = groups.size - 1
    var i = 0
    while (i < replacement.length) {
        val c = replacement[i]
        when {
            c == '\\' -> {
                require(i + 1 < replacement.length) { "character to be escaped is missing" }
                append(replacement[i + 1])
                i += 2
            }

            c == '$' -> {
                require(i + 1 < replacement.length) { "Illegal group reference" }
                when (val next = replacement[i + 1]) {
                    '$' -> {
                        append('$'); i += 2
                    }

                    '{' -> {
                        val end = replacement.indexOf('}', i + 2)
                        require(end >= 0) { "Named group reference is missing the closing brace" }
                        val name = replacement.substring(i + 2, end)
                        (match as HasGroups2).groups2[name]?.value?.let { append(it) }
                        i = end + 1
                    }

                    else -> {
                        require(next.isDigit()) { "Illegal group reference" }
                        var ref = next - '0'
                        var j = i + 2
                        while (j < replacement.length && replacement[j].isDigit() &&
                            ref * 10 + (replacement[j] - '0') <= lastGroup
                        ) {
                            ref = ref * 10 + (replacement[j] - '0')
                            j++
                        }
                        // The JVM reports a missing numeric group as IndexOutOfBoundsException, not as
                        // the IllegalArgumentException it uses for a malformed reference.
                        if (ref > lastGroup) throw IndexOutOfBoundsException("No group $ref")
                        // `groupValues` maps an unmatched group to "", which is exactly what the JVM
                        // appends for one.
                        append(match.groupValues[ref])
                        i = j
                    }
                }
            }

            else -> {
                append(c); i++
            }
        }
    }
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
