package mct.util.snbt

import io.kotest.assertions.shouldFail
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import net.benwoodworth.knbt.NbtCompound
import net.benwoodworth.knbt.NbtInt
import net.benwoodworth.knbt.NbtString
import net.benwoodworth.knbt.NbtTag


fun arbNbt(depth: Int = 4): Arb<NbtTag> {
    val leaf = Arb.choice(
        Arb.int().map { NbtInt(it) },
        Arb.string(1..5).map { NbtString(it) }
    )

    if (depth <= 0) return leaf

    return Arb.lazy {
        Arb.choice(
            leaf,
//            Arb.list(arbNbt(depth - 1), 1..3).map { it.asNbtListUnsafe() },
            Arb.map(
                Arb.string(1..5),
                arbNbt(depth - 1)
            ).map { NbtCompound(it) }
        )
    }
}

private fun parseTest(snbt: String) = shouldNotThrowAny {
    SnbtTag.decodeFromString(snbt)
}

class SnbtTest : FreeSpec({
    "empty list" {
        parseTest("[]") shouldBe SnbtList(0..1, emptyList())
    }

    "empty map" {
        parseTest("{}") shouldBe SnbtCompound(0..1, emptyMap())
    }

    "typed array" {
        parseTest("[I; 1, 2, 3]") shouldBe SnbtList(0..12, listOf(SnbtInt(4..4, 1), SnbtInt(7..7,2), SnbtInt(10..10, 3)))
        parseTest("[B; 1, 2, 3]") shouldBe SnbtList(0..12, listOf(SnbtInt(4..4, 1), SnbtInt(7..7,2), SnbtInt(10..10, 3)))

        shouldFail {
            parseTest("[X; 1, 2, 3]")
        }
    }


    "test parse" {
        parseTest("""{Rotation:[0F,0F],Tags:["info"],brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[2f,2f,2f]},text:{"bold":true,"color":"#FFFFFF","italic":false,"text":"Multiplayer","underlined":false},background:1342177280}""")
    }
})