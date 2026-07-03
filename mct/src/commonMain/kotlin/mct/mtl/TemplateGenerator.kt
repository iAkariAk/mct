package mct.mtl

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonElement
import mct.kit.TranslationMapping
import mct.serializer.MCTJson
import mct.serializer.Snbt
import mct.text.TextCompound
import mct.text.TextCompoundOneOrMany
import mct.text.decodeToTextCompoundOneOrMany
import mct.util.formatir.toIR
import net.benwoodworth.knbt.NbtTag

fun TextCompound.mtlize(): MTLExpression? = when {
    this !is TextCompound.Plain -> null
    extra.isEmpty() -> MTLLiteral(null, text)
    else -> MTLPair(null, MTLLiteral(null, text), MTLList(null, extra.map { it.mtlize() ?: return null }))
}


fun TextCompoundOneOrMany.mtlize(): MTLExpression? = when (this) {
    is TextCompoundOneOrMany.Many -> MTLList(null, value.map { it.mtlize() ?: return null })
    is TextCompoundOneOrMany.One -> value.mtlize()
}

internal inline fun String.tryDecodeAsTextCompound() = runCatching {
    MCTJson.decodeFromString<JsonElement>(this).toIR().decodeToTextCompoundOneOrMany()
}.getOrElse {
    runCatching {
        Snbt.decodeFromString<NbtTag>(this).toIR().decodeToTextCompoundOneOrMany()
    }.getOrNull()
}


fun Collection<String>.generateMTLXTemplate(placeholder: String = "TODO"): MTLX {
    val placeholderExpr = MTLLiteral(null, placeholder)
    val (_mtls, _raws) = asSequence()
        .map { it.tryDecodeAsTextCompound()?.mtlize() ?: it }
        .partition { it is MTLExpression }

    @Suppress("UNCHECKED_CAST")
    val mtls = _mtls.map { MTLMapping(null, it as MTLExpression, placeholderExpr) }

    @Suppress("UNCHECKED_CAST")
    val raws = _raws.associateWith { placeholder } as TranslationMapping
    return MTLX(
        mtlMappings = mtls,
        rawMappings = raws
    )
}