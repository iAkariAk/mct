package mct.pointer

import io.kotest.assertions.arrow.core.shouldNotRaise
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty


class DataPointerTest : FreeSpec({
    "codec test" - {
        "empty" - {
            "decode" {
                shouldNotRaise {
                    DataPointer.decodeFromString("")
                } shouldBe DataPointer.Terminator
            }

            "encode" {
                DataPointer.Terminator.encodeToString().shouldBeEmpty()
            }
        }


        "escape" - {
            val pointer1 = DataPointer { map("&hi>", terminate()) }
            val str1 = ">#&&hi&>"
            val pointer2 = DataPointer {
                map("a", array(0, map("b", map("op>=&a&", terminate()))))
            }
            val str2 = ">#a>0>#b>#op&>=&&a&&"

            "decodeFromString" - {
                "should work on normal string" {
                    shouldNotRaise {
                        withClue("Decode from $str2") {
                            DataPointer.decodeFromString(str2) shouldBeEqual pointer2
                        }
                    }
                }
                "should work on continuous >" {
                    val testPointerString2 = ">>>>>>>>#a>0>>>>#b>>>>>>#op&>=&a&"
                    shouldNotRaise {
                        withClue("Decode from $str2") {
                            DataPointer.decodeFromString(testPointerString2) shouldBeEqual pointer2
                        }
                    }
                }
            }
            "decode" - {
                "normal string" {
                    shouldNotRaise {
                        DataPointer.decodeFromString(str1) shouldBe pointer1
                        DataPointer.decodeFromString(str2) shouldBe pointer2
                    }
                }

                "continuous > should be ignored" {
                    val str2 = ">>>>>>>>#a>0>>>>#b>>>>>>#op&>=&a&"
                    shouldNotRaise {
                        DataPointer.decodeFromString(str2) shouldBeEqual pointer2
                    }
                }
            }

            "encode" {
                pointer1.encodeToString() shouldBe str1
                pointer2.encodeToString() shouldBe str2
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