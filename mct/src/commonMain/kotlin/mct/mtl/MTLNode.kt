package mct.mtl

import mct.model.text.ManyTextComponent
import mct.model.text.SingleTextComponent
import mct.model.text.TextComponent
import mct.util.IndentStringBuilder
import mct.util.withBlock

sealed interface MTLNode {
    val indices: IntRange?

    fun renderAppendTo(builder: IndentStringBuilder)
}

sealed interface MTLExpression : MTLNode

data class MTLLiteral(override val indices: IntRange?, val content: String) : MTLExpression {
    override fun renderAppendTo(builder: IndentStringBuilder): Unit = builder.run {
        append(content.wrappedMTLLiteral())
    }
}

data class MTLList(override val indices: IntRange?, val exprs: List<MTLExpression>) : MTLExpression {
    override fun renderAppendTo(builder: IndentStringBuilder): Unit = builder.run {
        withBlock("[", "]") {
            exprs.forEachIndexed { index, expression  ->
                expression.renderAppendTo(this)
                if (index != exprs.lastIndex) appendLine()
            }
        }
    }
}

data class MTLPair(override val indices: IntRange?, val left: MTLExpression, val right: MTLExpression) : MTLExpression {
    override fun renderAppendTo(builder: IndentStringBuilder): Unit = builder.run {
        withBlock("(", ")") {
            left.renderAppendTo(this)
            appendLine()
            right.renderAppendTo(this)
        }
    }
}

data class MTLMapping(override val indices: IntRange?, val left: MTLExpression, val right: MTLExpression) :
    MTLNode {
    override fun renderAppendTo(builder: IndentStringBuilder): Unit = builder.run {
        appendIndent()
        left.renderAppendTo(this)
        append(" ==> ")
        right.renderAppendTo(this)
    }
}

typealias MTLMappings = List<MTLMapping>

fun MTLMapping.isConsistent() = left.isConsistentBetweenWith(right)

fun MTLExpression.isConsistentBetweenWith(other: MTLExpression): Boolean = when (this) {
    is MTLList if other is MTLList -> exprs.size == other.exprs.size && exprs.zip(other.exprs)
        .all { (l, r) -> l.isConsistentBetweenWith(r) }

    is MTLPair if other is MTLPair -> left.isConsistentBetweenWith(other.left) && right.isConsistentBetweenWith(
        other.right
    )

    is MTLLiteral if other is MTLLiteral -> true
    else -> false
}

fun MTLMappings.find(text: TextComponent<*>): MTLMapping? = find { mapping ->
    text.matches(mapping.left)
}

fun TextComponent<*>.matches(expr: MTLExpression): Boolean = when (this) {
    is SingleTextComponent -> (expr is MTLLiteral && this is TextComponent.Plain && this.extra == null && this.text == expr.content)
            || (expr is MTLPair && expr.left is MTLLiteral && this is TextComponent.Plain && expr.left.content == this.text && (this.extra?.matches(expr.right) == true))

    is ManyTextComponent -> expr is MTLList && compounds.size == expr.exprs.size && compounds.zip(expr.exprs)
        .all { (actual, expected) -> actual.matches(expected) }
}
