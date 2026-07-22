package mct.command

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mct.MCTPattern
import mct.pointer.DataPointerPattern
import mct.pointer.compile
import mct.util.Regex2
import mct.util.groups2
import mct.util.offset
import mct.util.snbt.SnbtLexer
import mct.util.snbt.SnbtParser
import mct.util.snbt.SnbtTag
import mct.util.unreachable

// Refer to https://minecraft.wiki/w/Argument_types
context(_: Raise<IndexSelectError>)
private fun selectSnbt(baseIndex: Int, snbt: String, patterns: List<DataPointerPattern>?): List<SelectResult>? {
    val tag = runCatching<SnbtTag> {
        SnbtTag.decodeFromString(snbt)
    }.getOrElse {
        raise(IndexSelectError.Parse(snbt, it.message ?: "<null>"))
    }
    return tag.selectSnbt(baseIndex, snbt, 0, patterns)
}

context(_: Raise<IndexSelectError>)
private fun SnbtTag.selectSnbt(
    baseIndex: Int,
    snbt: String,
    snbtOffset: Int,
    patterns: List<DataPointerPattern>?
): List<SelectResult>? =
    extractTextsByPointer(snbt, snbtOffset)
        .filter { it.pointer.compile().matches(patterns) }
        .map {
            SelectResult(
                (baseIndex + it.indices.first)..(baseIndex + it.indices.last),
                it.content,
                it.syntax
            )
        }.toList().takeIf { it.isNotEmpty() } ?: emptyList()

context(_: Raise<IndexSelectError>)
private fun selectItemStackPropertyList(
    baseIndex: Int,
    str: String,
    patterns: ComponentPatterns?
): List<SelectResult> {
    val lexer = SnbtLexer(str, 0)
    val parser = SnbtParser(str, lexer)
    val buffer = StringBuilder()
    fun skipWhitespace() {
        while (lexer.index < str.length) {
            val ch = str[lexer.index]
            if (ch.isWhitespace()) lexer.index++
            else return
        }
    }

    fun toSelectResult(it: PointerWithExtensionForSnbt) =
        SelectResult(it.indices.offset(baseIndex), it.content, it.syntax)
    return sequence {
        while (lexer.index < str.length) {
            val ch = str[lexer.index]
            skipWhitespace()
            if (ch == '!') {
                var ch2 = str[lexer.index]
                while (ch2 != ',' && !ch2.isWhitespace() && lexer.index < str.length) {
                    ch2 = str[++lexer.index]
                }
                continue
            } else {
                var ch2 = str[lexer.index]
                while (ch2 != '=' && !ch2.isWhitespace()) {
                    buffer.append(ch2)
                    ch2 = str[++lexer.index]
                }
                val key = buffer.toString()
                buffer.clear()
                lexer.index++
                skipWhitespace()
                val valueStartIndex = lexer.index
                val value = parser.parse()
                val valueStr = str.substring(value.indices)
                val extracted = value.extractTextsByPointer(valueStr, valueStartIndex)
                val pattern = patterns?.findByCompoundKey(key)
                if (pattern != null) {
                    if (pattern.pattern == null) {
                        extracted.singleOrNull()
                            ?.let(::toSelectResult)
                            ?.let { yield(it) }
                    } else extracted
                        .filter { pattern.pattern.match(it.pointer.compile()) }
                        .map(::toSelectResult)
                        .let { yieldAll(it) }
                } else yieldAll(extracted.map(::toSelectResult))
                skipWhitespace()
                if (lexer.index >= str.length) break
                val ch3 = str[lexer.index]
                if (ch3 == ',') lexer.index++
            }
        }
    }.toList()
}

@Serializable
sealed interface ArgSelection {
    // null: select the entire
    context(_: Raise<IndexSelectError>)
    fun select(patterns: MCTPattern?, arg: MCCommand.Arg): List<SelectResult>?

    // brigadier:string
    @Serializable
    @SerialName("plain_entire")
    data object PlainEntire : ArgSelection {
        context(_: Raise<IndexSelectError>)
        override fun select(
            patterns: MCTPattern?,
            arg: MCCommand.Arg,
        ): List<SelectResult>? = null
    }

    // minecraft:component || minecraft:nbt_compound_tag || minecraft:nbt_tag || *minecraft:dialog* || minecraft:style
    @Serializable
    @SerialName("snbt_entire")
    data object SnbtEntire : ArgSelection {
        context(_: Raise<IndexSelectError>)
        override fun select(
            patterns: MCTPattern?,
            arg: MCCommand.Arg,
        ): List<SelectResult>? = selectSnbt(arg.indices.first, arg.content, patterns?.commandData)
    }


    // minecraft:item_stack
    @Serializable
    @SerialName("item_stack")
    data object ItemStack : ArgSelection {
        private val ITEM_STACK_REGX = Regex2("""^(?<id>[\w:.]+)(?:\[(?<new>.*)]|(?<old>\{.*}))$""")

        context(_: Raise<IndexSelectError>)
        override fun select(
            patterns: MCTPattern?,
            arg: MCCommand.Arg,
        ): List<SelectResult>? {
            val result = ITEM_STACK_REGX.matchEntire(arg.content) ?: raise(
                IndexSelectError.Parse(arg.content, "The arg didn't match ItemStack(${ITEM_STACK_REGX.pattern})")
            )
//            val id = result.groups2["id"]!!
            val new = result.groups2["new"]
            val old = result.groups2["old"]
            return when {
                new != null && new.value.isNotEmpty() -> selectItemStackPropertyList(
                    arg.indices.first + new.range.first,
                    new.value,
                    patterns?.commandComponent
                )

                old != null && old.value.isNotEmpty() -> selectSnbt(
                    arg.indices.first + old.range.first,
                    old.value,
                    patterns?.commandData
                )

                else -> unreachable
            }
        }
    }

    // minecraft:block_state
    @Serializable
    @SerialName("block_state")
    data object BlockState : ArgSelection {
        private val BLOCK_STATE_REGEX = Regex2("""^(?<id>[\w:.]+)(?:\[.*?])?(?<snbt>\{.*\})$""")

        context(_: Raise<IndexSelectError>)
        override fun select(
            patterns: MCTPattern?,
            arg: MCCommand.Arg,
        ): List<SelectResult>? {
            val result = BLOCK_STATE_REGEX.matchEntire(arg.content) ?: raise(
                IndexSelectError.Parse(arg.content, "The arg didn't match BlockState(${BLOCK_STATE_REGEX.pattern})")
            )
//            val id = result.groups2["id"]!!
            val snbt = result.groups2["snbt"]!!
            return selectSnbt(arg.indices.first + snbt.range.first, snbt.value, patterns?.commandData)
        }
    }
}
