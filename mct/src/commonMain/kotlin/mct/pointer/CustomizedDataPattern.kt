package mct.pointer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mct.util.toRegex2

@Serializable
sealed interface CustomizedDataPointerPattern {
    val negative: Boolean

    fun compile(): DataPointerPattern

    @Serializable
    @SerialName("right")
    data class RightPattern(
        val right: String,
        override val negative: Boolean = false
    ) : CustomizedDataPointerPattern {
        override fun compile() = DataPointerPattern { pointer ->
            pointer.matchesRight(right) != negative
        }
    }

    @Serializable
    @SerialName("regex")
    data class RegexPattern(
        val regex: String,
        override val negative: Boolean = false
    ) : CustomizedDataPointerPattern {
        private val _regex by lazy { regex.toRegex2() }
        override fun compile() = DataPointerPattern { pointer ->
            pointer.matches(_regex) != negative
        }
    }

}
