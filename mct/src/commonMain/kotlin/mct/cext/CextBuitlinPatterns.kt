package mct.cext

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mct.model.DataPointerPatternKind
import mct.pointer.DataPointerPatternSetBuilderScope
import mct.pointer.EqualPattern

@Serializable
sealed interface CextBuiltinPattern {
    val patterns: List<CextPatternEntry>
}

private class CextPatternBuilderScope {
    val patterns = mutableListOf<CextPatternEntry>()
    infix fun String.then(kind: CextFormatKind) = CextPatternEntry(this, kind).let(patterns::add)

    fun customOf(builder: DataPointerPatternSetBuilderScope.() -> Unit) = DataPointerPatternKind.customOf(builder)
}

private fun P(builder: CextPatternBuilderScope.() -> Unit): List<CextPatternEntry> {
    val scope = CextPatternBuilderScope()
    scope.apply(builder)
    return scope.patterns
}

@Serializable
@SerialName("level_dat")
data object LevelDat : CextBuiltinPattern {
    override val patterns = P {
        "level\\.dat" then CextFormatKind.Nbt(
            compression = Gzip,
            patterns = customOf {
                +EqualPattern(">#>#Data>#LevelName")
            }
        )
    }
}