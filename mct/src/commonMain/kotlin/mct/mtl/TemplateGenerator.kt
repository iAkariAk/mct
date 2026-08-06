package mct.mtl

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonElement
import mct.kit.TranslationMapping
import mct.model.text.ManyTextCompound
import mct.model.text.TextCompound
import mct.serializer.Snbt
import mct.util.decodeFromMCJson
import mct.util.formatir.toIR
import net.benwoodworth.knbt.NbtTag

fun TextCompound<*>.mtlize(): MTLExpression? = when {
    this is ManyTextCompound -> MTLList(null, compounds.map { it.mtlize() ?: return null })
    this !is TextCompound.Plain -> null
    else -> when (val _extra = extra) {
        null -> MTLLiteral(null, text)
        else -> MTLPair(
            null, MTLLiteral(null, text), when (_extra) {
                is TextCompound.Plain -> _extra.mtlize() ?: return null
                is ManyTextCompound -> MTLList(null, _extra.compounds.map { it.mtlize() ?: return null })
                else -> return null
            }
        )
    }
}



internal inline fun String.tryDecodeAsTextCompound() = runCatching {
    TextCompound.fromIR(decodeFromMCJson<JsonElement>(this).toIR())
}.getOrElse {
    runCatching {
        TextCompound.fromIR(Snbt.decodeFromString<NbtTag>(this).toIR())
    }.getOrNull()
}


fun Collection<String>.generateMTLXTemplate(placeholder: String = "TODO"): MTLX {
    val placeholderExpr = MTLLiteral(null, placeholder)
    val (_mtls, _raws) = asSequence()
        .map { it.tryDecodeAsTextCompound()?.mtlize() ?: it }
        .partition { it is MTLExpression }

    @Suppress("UNCHECKED_CAST")
    val mtls = _mtls.map { MTLMapping(null, it as MTLExpression, it.replaceWith(placeholderExpr)) }

    @Suppress("UNCHECKED_CAST")
    val raws = _raws.associateWith { placeholder } as TranslationMapping
    return MTLX(
        mtlMappings = mtls,
        rawMappings = raws
    )
}

private fun MTLExpression.replaceWith(placeholder: MTLLiteral): MTLExpression = when (this) {
    is MTLList -> copy(exprs = exprs.map { it.replaceWith(placeholder) })
    is MTLLiteral -> placeholder
    is MTLPair -> copy(left = left.replaceWith(placeholder), right = right.replaceWith(placeholder))
}
