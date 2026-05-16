package mct.util.snbt

import io.kotest.core.spec.style.FreeSpec
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


class SnbtTest : FreeSpec({
    val arb = arbNbt()
    val snbt =
        """{Rotation:[0F,0F],Tags:["info"],brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[2f,2f,2f]},text:{"bold":true,"color":"#FFFFFF","italic":false,"text":"Multiplayer","underlined":false},background:1342177280}"""

    "test parse" {
//        checkAll(arb) { tag ->
//            val snbt = Snbt.encodeToString(tag)
//
        val lexer = SnbtLexer(snbt)
        val parser = SnbtParser(snbt, lexer)
        val tag = parser.parse()
        println(tag)
//        }
    }
})