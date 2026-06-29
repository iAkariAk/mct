package mct.mtl

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import mct.model.patch.ExtractionGroup
import mct.model.patch.replaceSimply
import mct.serializer.MCTJson
import mct.serializer.Snbt
import mct.text.*
import mct.util.formatir.toIR
import mct.util.formatir.toJsonElement
import mct.util.formatir.toNbtTag
import net.benwoodworth.knbt.NbtTag


/**
 * Should keep all mapping follow [isConsistent];
 * otherwise will cause unexpected loss
 */
fun List<ExtractionGroup>.replaceByMTL(
    mappings: MTLMappings,
    default: (String) -> String?
) = replaceSimply {
    it.tryTransformTextCompound { compound ->
        mappings.find(compound)?.right?.let(compound::replace)
    } ?: default(it)
}

fun List<ExtractionGroup>.replaceByMTLX(mtlx: MTLX) = replaceByMTL(mtlx.mtlMappings, mtlx.rawMappings::get)

private inline fun String.tryTransformTextCompound(
    transform: (TextCompoundOneOrMany) -> TextCompoundOneOrMany?
): String? = runCatching {
    val e = MCTJson.decodeFromString<JsonElement>(this).toIR().decodeToTextCompoundOneOrMany()
    val r = transform(e)
    r?.encodeToIR()?.toJsonElement()?.let(MCTJson::encodeToString)
}.getOrElse {
    runCatching {
        val e = Snbt.decodeFromString<NbtTag>(this).toIR().decodeToTextCompoundOneOrMany()
        val r = transform(e)
        r?.encodeToIR()?.toNbtTag()?.let(Snbt::encodeToString)
    }.getOrNull()
}


/** CHECK [expr] By [isConsistent] before using the below */
internal fun TextCompound.replace(expr: MTLExpression): TextCompound {
    require(expr !is MTLList) { "expr shouldn't be MTLList" }
    return if (extra.isEmpty()) {
        require(expr is MTLLiteral) { "expr should be MTLiteral" }
        replaceText(expr.content)
    } else {
        require(expr is MTLPair) { "expr should be MTLPair" }
        require(expr.right is MTLList) { "expr.right should be MTLList" }
        substituteExtra(extra.zip(expr.right.exprs).map { (orig, expr) -> orig.replace(expr) })
    }
}

/** CHECK [expr] By [isConsistent] before using the below */

internal fun TextCompoundOneOrMany.replace(expr: MTLExpression) = when (this) {
    is Many -> {
        require(expr is MTLList) {
            "expr should be MTLList"
        }
        value.zip(expr.exprs).map { (l, r) -> l.replace(r) }.many()
    }

    is One -> {
        require(expr !is MTLList) {
            "expr shouldn't be MTLList"
        }
        value.replace(expr).one()
    }
}
