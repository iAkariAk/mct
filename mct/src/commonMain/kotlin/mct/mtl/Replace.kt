package mct.mtl

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import mct.kit.TranslationPool
import mct.model.patch.ExtractionGroup
import mct.model.patch.replaceSimply
import mct.model.text.*
import mct.serializer.MCTJson
import mct.serializer.Snbt
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

fun TranslationPool.translateByMTLX(mtlx: MTLX) = associateWith {
    it.tryTransformTextCompound { compound ->
        mtlx.mtlMappings.find(compound)?.right?.let(compound::replace)
    } ?: mtlx.rawMappings[it]
}

private inline fun String.tryTransformTextCompound(
    transform: (TextCompound<*>) -> TextCompound<*>?
): String? = runCatching {
    val tc = TextCompound.fromIR(MCTJson.decodeFromString<JsonElement>(this).toIR())
    val r = transform(tc)
    r?.encodeToIR()?.toJsonElement()?.let(MCTJson::encodeToString)
}.getOrElse {
    runCatching {
        val tc = TextCompound.fromIR(Snbt.decodeFromString<NbtTag>(this).toIR())
        val r = transform(tc)
        r?.encodeToIR()?.toNbtTag()?.let(Snbt::encodeToString)
    }.getOrNull()
}


/** CHECK [expr] By [isConsistent] before using the below */
internal fun TextCompound<*>.replace(expr: MTLExpression): TextCompound<*> = when (this) {
    is ManyTextCompound -> {
        require(expr is MTLList) {
            "expr should be MTLList"
        }
        require(compounds.size == expr.exprs.size) {
            "compounds.size == expr.exprs.size"
        }
        copy().apply {
            compounds = compounds.zip(expr.exprs).map { (l, r) -> l.replace(r) }
        }
    }

    is SingleTextCompound<*> -> {
        require(expr !is MTLList) { "expr shouldn't be MTLList" }
        val _extra = extra
        if (_extra == null) { // Flatten Plain
            require(expr is MTLLiteral) { "expr should be MTLiteral" }
            replaceText(expr.content)
        } else {
            require(expr is MTLPair) { "expr should be MTLPair" }
            require(expr.left is MTLLiteral) { "expr.left should be MTLLiteral" } // left is [text]
            substitute(expr.left.content, _extra.replace(expr.right))
        }
    }
}

