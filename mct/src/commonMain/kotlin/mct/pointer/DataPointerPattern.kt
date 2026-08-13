@file:Suppress("FunctionName", "UnusedReceiverParameter")

package mct.pointer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mct.model.patch.ContentKind
import mct.util.Regex2
import mct.util.toRegex2
import org.intellij.lang.annotations.Language

@Serializable
sealed interface DataPointerPattern {
    val kind: ContentKind

    fun match(pointer: CompiledDataPointer): Boolean

    @SerialName("right")
    data class Right(
        val right: String,
        val negative: Boolean = false,
        override val kind: ContentKind = Text
    ) : DataPointerPattern {
        override fun match(pointer: CompiledDataPointer) = pointer.matchesRight(right) != negative
    }

    @SerialName("regex")
    data class Regex(
        @Language("RegExp") val regex: String,
        val negative: Boolean = false,
        override val kind: ContentKind = Text
    ) : DataPointerPattern {
        private val _r by lazy { regex.toRegex2() }
        override fun match(pointer: CompiledDataPointer): Boolean = pointer.matches(_r) != negative
    }
}

data class CompiledDataPointer(val pointer: DataPointer) {
    private val str = pointer.encodeToString()

    fun matches(
        patterns: Iterable<DataPointerPattern>?,
    ) = patterns?.any { it.match(this) } ?: true

    fun matched(
        patterns: Iterable<DataPointerPattern>,
    ): DataPointerPattern? = patterns.find { it.match(this) }

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

inline fun S.RightPattern(right: String, negative: Boolean = false, kind: ContentKind = Text) =
    DataPointerPattern.Right(right, negative, kind)

inline fun S.RegexPattern(@Language("RegExp") regex: String, negative: Boolean = false, kind: ContentKind = Text) =
    DataPointerPattern.Regex(regex, negative, kind)
