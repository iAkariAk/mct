@file:Suppress("unused", "FunctionName")

package mct.dp.mcfunction

import mct.util.BuilderMaker
import org.intellij.lang.annotations.Language


@BuilderMaker
class CommandBuilderPreConditionScope(val command: String) {
    internal val result = mutableSetOf<ExtractPattern>()

    infix fun PreCondition.then(builder: CommandBuilderIndexSelectorScope.() -> Unit) {
        val scope = CommandBuilderIndexSelectorScope()
        scope.run(builder)
        scope.result.mapTo(result) { (selector, post) ->
            ExtractPattern(command, this, selector, post)
        }
    }

    fun Regex(@Language("RegExp") regex: String): PreCondition = PreCondition.Companion.Regex(regex)
    fun WithSize(size: Int, strict: Boolean = false): PreCondition = PreCondition.Companion.WithSize(size, strict)

    fun Any(): PreCondition = PreCondition.Companion.Any
    fun And(vararg conditions: PreCondition): PreCondition = PreCondition.Companion.And(conditions.asList())
    fun Or(vararg conditions: PreCondition): PreCondition = PreCondition.Companion.Or(conditions.asList())
    fun None(vararg conditions: PreCondition): PreCondition = PreCondition.Companion.None(conditions.asList())
}

@BuilderMaker
class CommandBuilderIndexSelectorScope {
    internal val result = mutableSetOf<Pair<IndexSelector, PostCondition>>()
    private fun Pair<IndexSelector, PostCondition>.bind() = also(result::add)

    private fun IndexSelector.bind(conditions: Set<PostCondition>) {
        conditions.mapTo(result) {
            this to it
        }
    }

    infix fun IndexSelector.then(builder: CommandBuilderPostConditionScope.() -> Unit) {
        val scope = CommandBuilderPostConditionScope()
        scope.run(builder)
        bind(scope.result)
    }

    fun IndexSelector.withAry() {
        bind(setOf(PostCondition.Companion.Any))
    }

    operator fun IndexSelector.unaryPlus() = withAry()

    fun GreedyPositions(beginArgPosition: Int = 0) = IndexSelector.Greedy(beginArgPosition)
    fun Positions(vararg positions: Int) = IndexSelector.NonGreedy(positions.asList().associateWith { null })
    fun Positions(vararg positionsWithSelections: Pair<Int, IndexSelection>) = IndexSelector.NonGreedy(positionsWithSelections.toMap())
}

@BuilderMaker
class CommandBuilderPostConditionScope {
    internal val result = mutableSetOf<PostCondition>()
    private fun PostCondition.bind() = also(result::add)

    fun Matches(comment: String = "<anonymous>", matcher: (MCCommand, MCCommand.Arg) -> Boolean): PostCondition =
        object : PostCondition {
            override fun toString() = "Matches{$comment}"
            override fun matches(command: MCCommand, arg: MCCommand.Arg) = matcher(command, arg)
        }.bind()

    fun At(position: Int, condition: CommandBuilderPostConditionScope.() -> Unit) {
        val scope = CommandBuilderPostConditionScope()
        scope.run(condition)
        scope.result.mapTo(result) { cond ->
            PostCondition.Companion.At(position, cond)
        }
    }

    fun Regex(@Language("RegExp") regex: String): PostCondition = PostCondition.Companion.MatchRegex(regex).bind()

    fun Any(): PostCondition = PostCondition.Companion.Any.bind()
    fun And(vararg conditions: PostCondition): PostCondition = PostCondition.Companion.And(conditions.asList()).bind()
    fun Or(vararg conditions: PostCondition): PostCondition = PostCondition.Companion.Or(conditions.asList()).bind()
    fun None(vararg conditions: PostCondition): PostCondition = PostCondition.Companion.None(conditions.asList()).bind()
}


@BuilderMaker
class RootBuilderScope {
    internal val patterns = mutableMapOf<String, List<ExtractPattern>>()

    fun command(
        command: String,
        preCondition: PreCondition,
        indexSelector: IndexSelector,
        postCondition: PostCondition
    ) {
        val p = patterns(command)
        p.add(ExtractPattern(command, preCondition, indexSelector, postCondition))
    }

    fun command(
        command: String,
        builder: CommandBuilderPreConditionScope.() -> Unit
    ) {
        val scope = CommandBuilderPreConditionScope(command)
        context(this) {
            scope.builder()
        }
        val p = patterns(command)
        p.addAll(scope.result)
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun patterns(command: String): MutableList<ExtractPattern> =
        patterns.getOrPut(command) { mutableListOf() } as MutableList<ExtractPattern>
}

fun PatternSet(builder: RootBuilderScope.() -> Unit): Map<String, List<ExtractPattern>> {
    val scope = RootBuilderScope()
    context(scope) {
        scope.builder()
    }
    return scope.patterns
}
