package mct.pointer

import io.kotest.assertions.arrow.core.shouldNotRaise
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe


class DataPointerTest : FreeSpec({
    "codec test" - {
        val testPointerString = ">#a>0>#b>#op&>="
        val testPointer = DataPointer {
            map("a", array(0, map("b", map("op>=", terminate()))))
        }

        "encodeToShould" {
            val encoded = testPointer.encodeToString()
            withClue("Encode from $testPointer") {
                testPointerString shouldBeEqual encoded
            }
        }

        "decodeFromString" - {
            "should work on normal string" {
                shouldNotRaise {
                    withClue("Decode from $testPointerString") {
                        DataPointer.decodeFromString(testPointerString) shouldBeEqual testPointer
                    }
                }
            }
            "should work on continuous >" {
                val testPointerString2 = ">>>>>>>>#a>0>>>>#b>>>>>>#op&>="
                shouldNotRaise {
                    withClue("Decode from $testPointerString") {
                        DataPointer.decodeFromString(testPointerString2) shouldBeEqual testPointer
                    }
                }
            }
        }
    }

    "sort test" {
        val pointers = listOf(
            ">#a>#b>#c>#d",
            ">#a>#b",
            ">#a>#c>#d",
            ">#g>#a>#d>#d",
            ">#c>0>#d>#d",
            ">#a>1>#d>#f",
            ">#a>1>#d>#f#c",
            ">#a>1>#d>#f#d",
            ">#a>1>#d>#f#d#a",
        ).map {
            shouldNotRaise {
                DataPointer.decodeFromString(it)
            }
        }

        pointers.sorted().map { it.encodeToString() } shouldBe listOf(
            ">#a>#b",
            ">#a>#b>#c>#d",
            ">#a>#c>#d",
            ">#a>1>#d>#f",
            ">#a>1>#d>#f#c",
            ">#a>1>#d>#f#d",
            ">#a>1>#d>#f#d#a",
            ">#c>0>#d>#d",
            ">#g>#a>#d>#d",
        )
    }
})