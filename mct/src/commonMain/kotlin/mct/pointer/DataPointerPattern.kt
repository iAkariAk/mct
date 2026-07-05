@file:Suppress("FunctionName", "UnusedReceiverParameter")

package mct.pointer

import mct.util.Regex2
import mct.util.toRegex2
import org.intellij.lang.annotations.Language


fun interface DataPointerPattern {
    fun match(pointer: CompiledDataPointer): Boolean
}

data class CompiledDataPointer(val pointer: DataPointer) {
    private val str = pointer.encodeToString()

    fun matches(
        patterns: Iterable<DataPointerPattern>?,
    ) = patterns?.any { it.match(this) } ?: true

    fun matches(regex: Regex2) = regex.containsMatchIn(str)
    fun matchesRight(right: String) = str.endsWith(right)
    fun matchesRight(right: CompiledDataPointer) = str.endsWith(right.str)
}

fun DataPointer.compile() = CompiledDataPointer(this)

fun DataPointer.matches(
    patterns: Iterable<DataPointerPattern>?,
) = compile().matches(patterns)

fun DataPointer.matches(regex: Regex2) =
    regex.containsMatchIn(encodeToString())

fun DataPointer.matchesRight(right: String) =
    encodeToString().endsWith(right)

fun DataPointer.matchesRight(right: DataPointer) =
    matchesRight(right.encodeToString())

inline fun DataPointer.matchesRight(right: DataPointerBuilderDsl.() -> DataPointer) =
    matchesRight(DataPointerBuilderDsl.run(right))


fun PatternSet(action: DataPointerPatternSetBuilderScope.() -> Unit): List<DataPointerPattern> {
    val result = mutableListOf<DataPointerPattern>()
    val scope = object : DataPointerPatternSetBuilderScope {
        override fun DataPointerPattern.unaryPlus() {
            result += this
        }

        override fun dependsOn(patterns: List<DataPointerPattern>) {
            result += patterns
        }
    }
    scope.apply(action)
    return result
}

interface DataPointerPatternSetBuilderScope {
    operator fun DataPointerPattern.unaryPlus()

    fun dependsOn(patterns: List<DataPointerPattern>)
}

private typealias S = DataPointerPatternSetBuilderScope

inline fun S.RightPattern(right: String) = DataPointerPattern { it.matchesRight(right) }
inline fun S.RegexPattern(@Language("RegExp") regex: String): DataPointerPattern {
    val _r = regex.toRegex2()
    return DataPointerPattern { it.matches(_r) }
}
