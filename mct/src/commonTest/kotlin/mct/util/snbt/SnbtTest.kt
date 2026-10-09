package mct.util.snbt

import io.kotest.assertions.shouldFail
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import mct.util.indexRangeOf
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

private inline fun <T> matchFully(constructor: (IntRange, T) -> SnbtTag, str: String, expected: T) =
    parseTest(str) shouldBe constructor(str.indices, expected)

private inline fun matchByte(str: String, expected: Byte) = matchFully(::SnbtByte, str, expected)
private inline fun matchShort(str: String, expected: Short) = matchFully(::SnbtShort, str, expected)
private inline fun matchInt(str: String, expected: Int) = matchFully(::SnbtInt, str, expected)
private inline fun matchLong(str: String, expected: Long) = matchFully(::SnbtLong, str, expected)
private inline fun matchFloat(str: String, expected: Float) = matchFully(::SnbtFloat, str, expected)
private inline fun matchDouble(str: String, expected: Double) = matchFully(::SnbtDouble, str, expected)


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
            val snbt = "{Name:generic.max_health,Base:10, ObjK: X-ray}"
            parseTest(snbt) shouldBe SnbtCompound(
                snbt.indices, mapOf(
                    "Name" to SnbtString(snbt.indexRangeOf("generic.max_health")!!, "generic.max_health", null),
                    "Base" to SnbtInt(snbt.indexRangeOf("10")!!, 10),
                    "ObjK" to SnbtString(snbt.indexRangeOf("X-ray")!!, "X-ray", null),
                )
            )
        }
    }

    "integer promotion" {
        val int = "114514"
        val long = "11451419198100000"
        parseTest(int) shouldBe SnbtInt(int.indices, int.toInt())
        parseTest(long) shouldBe SnbtLong(long.indices, long.toLong())
    }

    "dot literal list" {
        val snbt = """
            {Dialog:[I,t," ",w,i,l,l," ",b,e," ",w,o,n,d,e,r,f,u,l,.]}
        """.trimIndent()
        parseTest(snbt)
    }

    "single quote escape" {
        val snbt = """'Shina\'s Mimi and Mimi\'s Shina \\\\ '"""
        val result = parseTest(snbt) as SnbtString
        result shouldBe SnbtString(snbt.indices, snbt, '\'')
        result.content shouldBe """Shina's Mimi and Mimi's Shina \\ """
    }

    "tail comma" {
        val snbt1 = "{height:0.2f, width:0.65f ,}"
        parseTest(snbt1) shouldBe SnbtCompound(
            snbt1.indices, mapOf(
                "height" to SnbtFloat(8..11, 0.2f),
                "width" to SnbtFloat(20..24, 0.65f)
            )
        )

        val snbt2 = "[1,2,3,]"
        parseTest(snbt2) shouldBe SnbtList(
            snbt2.indices, listOf(
                SnbtInt(1..1, 1),
                SnbtInt(3..3, 2),
                SnbtInt(5..5, 3),
            )
        )
    }

    "number" - {
        "normal integer" {
            matchInt("1234", 1234)
            matchInt("+1234", 1234)
            matchInt("-1234", -1234)
        }

        "normal float" {
            matchFloat("1234f", 1234f)
            matchFloat("+1234f", 1234f)
            matchFloat("-1234f", -1234f)
            matchDouble("-1234.0", -1234.0)
        }

        ". leading float" {
            matchFloat(".114f", .114f)
            matchDouble(".114", .114)
        }

        "exponential float" {
            matchFloat("3e10f", 3e10f)
            matchDouble("3e10", 3e10)
        }

        "hex" {
            matchByte("0xFb", 0xF)
            matchShort("0xFFs", 0xFF)
            matchInt("0xFF", 0xFF)
            matchLong("0xFFl", 0xFF)
        }

        "bin" {
            matchByte("0b111b", 0b111.toByte())
            matchShort("0b111s", 0b111)
            matchInt("0b111", 0b111)
            matchLong("0b111l", 0b111)
        }
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