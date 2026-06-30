package mct.mtl

import mct.text.TextCompoundOneOrMany
import mct.text.many
import mct.text.one
import mct.util.buildIndentedString
import mct.util.withBlock

sealed interface MTLNode {
    val indices: IntRange?

    fun render(): String
}

sealed interface MTLExpression : MTLNode

data class MTLLiteral(override val indices: IntRange?, val content: String) : MTLExpression {
    override fun render() = content.escapeMTLLiteral()
}

data class MTLList(override val indices: IntRange?, val exprs: List<MTLExpression>) : MTLExpression {
    override fun render() = buildIndentedString {
        withBlock("[", "]", appendTail = false) {
            exprs.forEach { e ->
                appendLine(e.render())
            }
        }
    }
}

data class MTLPair(override val indices: IntRange?, val left: MTLExpression, val right: MTLExpression) : MTLExpression {
    override fun render() = buildIndentedString {
        withBlock("(", ")") {
            append(left.render())
            appendLine()
            append(right.render())
        }
    }
}

data class MTLMapping(override val indices: IntRange?, val left: MTLExpression, val right: MTLExpression) :
    MTLNode {
    override fun render() = "${left.render()} ==> ${right.render()}"
}

typealias MTLMappings = List<MTLMapping>

fun MTLMapping.isConsistent() = left.isConsistentBetweenWith(right)

fun MTLExpression.isConsistentBetweenWith(other: MTLExpression): Boolean = when (this) {
    is MTLList if other is MTLList -> exprs.zip(other.exprs).all { (l, r) -> l.isConsistentBetweenWith(r) }
    is MTLPair if other is MTLPair -> left.isConsistentBetweenWith(other.left) && right.isConsistentBetweenWith(
        other.right
    )

    is MTLLiteral if other is MTLLiteral -> true
    else -> false
}

fun MTLMappings.find(text: TextCompoundOneOrMany): MTLMapping? = find { mapping ->
    text.matches(mapping.left)
}

fun TextCompoundOneOrMany.matches(expr: MTLExpression): Boolean = when (this) {
    is One -> (expr is MTLLiteral && value is Plain && value.extra.isEmpty() && value.text == expr.content) || (expr is MTLPair && value.extra.many()
        .matches(expr.right))

    is Many -> expr is MTLList && value.zip(expr.exprs)
        .all { (actual, expected) -> actual.one().matches(expected) }
}