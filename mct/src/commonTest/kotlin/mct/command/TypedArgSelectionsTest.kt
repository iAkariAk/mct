package mct.command

import io.kotest.assertions.arrow.core.shouldNotRaise
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import mct.MCTPattern
import mct.dp.mcfunction.backfillMCFunction
import mct.model.patch.DatapackReplacement.MCFunction
import mct.model.patch.SnbtSyntaxKind
import mct.util.offset

class TypedArgSelectionsTest : FreeSpec({
    fun selectItemStack(raw: String, startIndex: Int = 0): List<SelectResult> {
        val arg = MCCommand.Arg(
            relativeIndices = startIndex..startIndex + raw.lastIndex,
            indices = startIndex..startIndex + raw.lastIndex,
            content = raw,
        )
        return shouldNotRaise { ArgSelection.ItemStack.select(MCTPattern.Default, arg).orEmpty() }
    }

    fun selectBlockState(raw: String, startIndex: Int = 0): List<SelectResult> {
        val arg = MCCommand.Arg(
            relativeIndices = startIndex..startIndex + raw.lastIndex,
            indices = startIndex..startIndex + raw.lastIndex,
            content = raw,
        )
        return shouldNotRaise { ArgSelection.BlockState.select(MCTPattern.Default, arg).orEmpty() }
    }

    "item_name selection keeps its absolute source range" {
        val itemStack = "minecraft:stick[item_name='Hello']"
        val startIndex = 40

        val result = selectItemStack(itemStack, startIndex)

        result shouldBe listOf(
            SelectResult(
                indices = (startIndex + itemStack.indexOf("'Hello'"))..(startIndex + itemStack.indexOf("'Hello'") + "'Hello'".lastIndex),
                content = "'Hello'",
                syntax = SnbtSyntaxKind.SingleQuoteString,
            )
        )
    }

    "selected component ranges round-trip through mcfunction backfill" {
        val command = "give @s minecraft:stick[item_name='Hello']"
        val itemStackStart = command.indexOf("minecraft:stick")
        val itemStack = command.substring(itemStackStart)
        val slice = selectItemStack(itemStack, itemStackStart).single()

        command.substring(slice.indices) shouldBe slice.content
        command.backfillMCFunction(
            listOf(MCFunction(slice.indices, "'你好'", slice.syntax))
        ) shouldBe "give @s minecraft:stick[item_name='你好']"
    }

    "nested text component range points at the complete component value" {
        val itemStack = "minecraft:stick[item_name='{\"text\":\"Hello\"}']"
        val startIndex = 13
        val slice = selectItemStack(itemStack, startIndex).single()

        slice.content shouldBe "'{\"text\":\"Hello\"}'"
        itemStack.substring(slice.indices.offset(-startIndex)) shouldBe slice.content
    }

    "block_state selection ignores properties and keeps the NBT value source range" {
        val blockState = "minecraft:chest[facing=north,waterlogged=false]{CustomName:'{\"text\":\"Treasure\"}'}"
        val customName = "'{\"text\":\"Treasure\"}'"
        val startIndex = 27

        val result = selectBlockState(blockState, startIndex)

        result shouldBe listOf(
            SelectResult(
                indices = (startIndex + blockState.indexOf(customName))..(startIndex + blockState.indexOf(customName) + customName.lastIndex),
                content = customName,
                syntax = SnbtSyntaxKind.SingleQuoteString,
            )
        )
    }
})
