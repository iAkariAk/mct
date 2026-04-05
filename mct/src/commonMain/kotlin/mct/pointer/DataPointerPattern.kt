package mct.pointer

import org.intellij.lang.annotations.Language


fun interface DataPointerPattern {
    fun match(pointer: DataPointer): Boolean
}

fun DataPointer.matches(regex: Regex) =
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
    }
    scope.apply(action)
    return result
}

interface DataPointerPatternSetBuilderScope {
    operator fun DataPointerPattern.unaryPlus()
}

private typealias S = DataPointerPatternSetBuilderScope

inline fun S.RightPattern(right: String) = DataPointerPattern { it.matchesRight(right) }
inline fun S.RegexPattern(@Language("RegExp") regex: String): DataPointerPattern {
    val _r = regex.toRegex()
    return DataPointerPattern { it.matches(_r) }
}
