package mct.pointer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
        private val _regex by lazy { regex.toRegex() }
        override fun compile() = DataPointerPattern { pointer ->
            pointer.matches(_regex) != negative
        }
    }

}
