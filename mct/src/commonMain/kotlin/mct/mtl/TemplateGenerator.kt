package mct.mtl

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonElement
import mct.command.MCCommandJson
import mct.kit.TranslationMapping
import mct.model.text.ManyTextComponent
import mct.model.text.TextComponent
import mct.serializer.Snbt
import mct.util.decodeFromString
import mct.util.formatir.toIR
import net.benwoodworth.knbt.NbtTag

fun TextComponent<*>.mtlize(): MTLExpression? = when {
    this is ManyTextComponent -> MTLList(null, compounds.map { it.mtlize() ?: return null })
    this !is TextComponent.Plain -> null
    else -> when (val _extra = extra) {
        null -> MTLLiteral(null, text)
        else -> MTLPair(
            null, MTLLiteral(null, text), when (_extra) {
                is TextComponent.Plain -> _extra.mtlize() ?: return null
                is ManyTextComponent -> MTLList(null, _extra.compounds.map { it.mtlize() ?: return null })
                else -> return null
            }
        )
    }
}



internal inline fun String.tryDecodeAsTextComponent() = runCatching {
    TextComponent.fromIR(MCCommandJson.decodeFromString<JsonElement>(this).toIR())
}.getOrElse {
    runCatching {
        TextComponent.fromIR(Snbt.decodeFromString<NbtTag>(this).toIR())
    }.getOrNull()
}


fun Collection<String>.generateMTLXTemplate(placeholder: String = "TODO"): MTLX {
    val placeholderExpr = MTLLiteral(null, placeholder)
    val (_mtls, _raws) = asSequence()
        .map { it.tryDecodeAsTextComponent()?.mtlize() ?: it }
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
