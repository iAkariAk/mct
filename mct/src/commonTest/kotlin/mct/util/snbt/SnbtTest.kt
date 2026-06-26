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
import org.intellij.lang.annotations.Language


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

private fun parseTest(@Language("snbt") snbt: String) = shouldNotThrowAny {
    SnbtTag.decodeFromString(snbt).also {
        println(it)
    }
}

class SnbtTest : FreeSpec({
    "empty list" {
        parseTest("[]") shouldBe SnbtList(0..1, emptyList())
    }

    "empty map" {
        parseTest("{}") shouldBe SnbtCompound(0..1, emptyMap())
    }

    "typed array" {
        parseTest("[I; 1, 2, 3]") shouldBe SnbtList(
            0..12,
            listOf(SnbtInt(4..4, 1), SnbtInt(7..7, 2), SnbtInt(10..10, 3))
        )
        parseTest("[B; 1, 2, 3]") shouldBe SnbtList(
            0..12,
            listOf(SnbtByte(4..4, 1), SnbtByte(7..7, 2), SnbtByte(10..10, 3))
        )

        shouldNotThrowAny {
            parseTest("[B; 1b, 2b]")
        }

        shouldFail {
            parseTest("[I; 1.0, 10.0]")
        }

        shouldFail {
            parseTest("[X; 1, 2, 3]")
        }
    }

    "infer type without suffix" {
        parseTest("[0, 1, 2]") shouldBe SnbtList(0..8, listOf(SnbtInt(1..1, 0), SnbtInt(4..4, 1), SnbtInt(7..7, 2)))
    }

    "literal" {
        shouldNotThrowAny {
            parseTest("""{Name:generic.max_health,Base:10, ObjK: X-ray}""")
        }
    }

    "integer promotion" {
        val int = "114514"
        val long = "11451419198100000"
        parseTest(int) shouldBe SnbtInt(int.indices, int.toInt())
        parseTest(long) shouldBe SnbtLong(long.indices, long.toLong())
    }

    "dot literal list" {
        parseTest(
            """
            {Dialog:[I,t," ",w,i,l,l," ",b,e," ",w,o,n,d,e,r,f,u,l,.]}
        """.trimIndent()
        )
    }

    "single quote escape" {
        parseTest(
            """
            'Shina\'s Mimi and Mimi\'s Shina \\\\ '   
        """.trimIndent()
        )
    }

    "tail comma" {
        parseTest(
            """
            {height:0.2f, width:0.65f ,}
        """.trimIndent()
        )

        parseTest(
            """
            [1,2,3,]
        """.trimIndent()
        )
    }

    "anyway" {
        parseTest(
            """
                {
                        UUID2: [I;0,0,0,0]
                }
        """.trimIndent()
        )
    }
})