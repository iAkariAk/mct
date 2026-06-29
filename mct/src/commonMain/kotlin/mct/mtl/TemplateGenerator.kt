package mct.mtl

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonElement
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

internal inline fun String.tryDecodeAsTextCompoound() = runCatching {
    MCTJson.decodeFromString<JsonElement>(this).toIR().decodeToTextCompoundOneOrMany()
}.getOrElse {
    runCatching {
        Snbt.decodeFromString<NbtTag>(this).toIR().decodeToTextCompoundOneOrMany()
    }.getOrNull()
}


fun Collection<String>.generateMTLX(): String {
    val tmp = asSequence()
        .map(String::tryDecodeAsTextCompoound)
        .map { it?.mtlize() }
        .zip(asSequence())
        .groupBy { (it, _) -> it != null }
    return buildString {
        append(MTLX.SEPARATOR_MTL)
        appendLine()
        tmp[true]?.forEach { (expr, _) ->
            val mapping = MTLMapping(null, expr!!, expr)
            append(mapping.render())
            appendLine()
        }
        append(MTLX.SEPARATOR_RAW)
        appendLine()
        tmp[false]?.forEach { (_, raw) ->
            append(raw.escapeMTLLiteral())
            append(" ==> ")
            append("TODO")
            appendLine()
        }
    }
}