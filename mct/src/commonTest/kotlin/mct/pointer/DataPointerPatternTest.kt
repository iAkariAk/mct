package mct.pointer

import io.kotest.assertions.arrow.core.shouldNotRaise
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

fun ptr(s: String) = shouldNotRaise { DataPointer.decodeFromString(s) }
infix fun DataPointer.shouldMatch(patterns: List<DataPointerPattern>) = matches(patterns) shouldBe true
infix fun DataPointer.shouldNotMatch(patterns: List<DataPointerPattern>) = matches(patterns) shouldBe false
fun shouldMatch(ptr: String, patterns: List<DataPointerPattern>) = ptr(ptr) shouldMatch patterns
fun shouldNotMatch(ptr: String, patterns: List<DataPointerPattern>) = ptr(ptr) shouldNotMatch patterns

class DataPointerPatternTest : FreeSpec({
    "DataPointer.matchesRight" - {
        "positive - simple suffix" {
            val ptr = shouldNotRaise { DataPointer.decodeFromString(">#display>#Name") }
            ptr.matchesRight("#display>#Name") shouldBe true
        }

        "positive - nested path" {
            val ptr = shouldNotRaise { DataPointer.decodeFromString(">#a>#b>#c>#d") }
            ptr.matchesRight("#c>#d") shouldBe true
        }

        "negative - wrong suffix" {
            val ptr = shouldNotRaise { DataPointer.decodeFromString(">#display>#Lore") }
            ptr.matchesRight("#display>#Name") shouldBe false
        }

        "negative - partial match not suffix" {
            val ptr = shouldNotRaise { DataPointer.decodeFromString(">#display>#Name>#extra") }
            ptr.matchesRight("#display>#Name") shouldBe false
        }

        "escaped characters in map key" {
            val ptr = shouldNotRaise { DataPointer.decodeFromString(">#op&>=#custom_name") }
            // encodeToString escapes '>' in map keys as '&>', so the suffix uses the escaped form
            ptr.matchesRight("#op&>=#custom_name") shouldBe true
        }

        "with DataPointer argument" {
            val ptr = shouldNotRaise { DataPointer.decodeFromString(">#a>#b>#c") }
            val suffix = shouldNotRaise { DataPointer.decodeFromString(">#b>#c") }
            ptr.matchesRight(suffix) shouldBe true
        }

        "with builder DSL" {
            val ptr = shouldNotRaise { DataPointer.decodeFromString(">#display>#Name") }
            ptr.matchesRight { map("display", map("Name", terminate())) } shouldBe true
        }
    }

    "DataPointer.matches(Regex)" - {
        "positive - simple regex" {
            val ptr = shouldNotRaise { DataPointer.decodeFromString(">#components>#lore>3") }
            ptr.matches("""#lore>\d+$""".toRegex()) shouldBe true
        }

        "positive - alternation" {
            val ptr = shouldNotRaise { DataPointer.decodeFromString(">#front_text>#messages>2") }
            ptr.matches("""#(front|back)_text>#messages>\d+$""".toRegex()) shouldBe true
        }

        "negative - non-numeric lore index" {
            val ptr = shouldNotRaise { DataPointer.decodeFromString(">#components>#lore>#title") }
            ptr.matches("""#lore>\d+$""".toRegex()) shouldBe false
        }

        "regex against entity CustomName path" {
            val ptr = shouldNotRaise { DataPointer.decodeFromString(">#>#Entities>0>#CustomName") }
            ptr.matches("""(>#Entities>\d+|>#SpawnData>#entity)>#CustomName$""".toRegex()) shouldBe true
        }
    }

    "PatternSet DSL" - {
        "RightPattern matches correctly" {
            val patterns = PatternSet {
                +RightPattern("#display>#Name")
                +RightPattern("#display>#Lore")
            }

            val ptrName = shouldNotRaise { DataPointer.decodeFromString(">#display>#Name") }
            val ptrLore = shouldNotRaise { DataPointer.decodeFromString(">#display>#Lore") }
            val ptrOther = shouldNotRaise { DataPointer.decodeFromString(">#display>#Other") }

            ptrName.matches(patterns) shouldBe true
            ptrLore.matches(patterns) shouldBe true
            ptrOther.matches(patterns) shouldBe false
        }

        "RegexPattern matches correctly" {
            val patterns = PatternSet {
                +RegexPattern("""#(front|back)_text>#messages>\d+$""")
            }

            val ptrFront = shouldNotRaise { DataPointer.decodeFromString(">#front_text>#messages>0") }
            val ptrBack = shouldNotRaise { DataPointer.decodeFromString(">#back_text>#messages>3") }
            val ptrOther = shouldNotRaise { DataPointer.decodeFromString(">#side_text>#messages>0") }

            ptrFront.matches(patterns) shouldBe true
            ptrBack.matches(patterns) shouldBe true
            ptrOther.matches(patterns) shouldBe false
        }
    }

    "CustomizedDataPointerPattern" - {
        "RightPattern compile - positive (negative=false)" {
            val pattern = CustomizedDataPointerPattern.RightPattern(
                right = "#display>#Name",
            ).compile()

            val ptr = shouldNotRaise { DataPointer.decodeFromString(">#display>#Name") }
            pattern.match(ptr) shouldBe true
        }

        "RightPattern compile - negative flag inverts result" {
            val pattern = CustomizedDataPointerPattern.RightPattern(
                right = "#display>#Name",
                negative = true
            ).compile()

            val ptrMatch = shouldNotRaise { DataPointer.decodeFromString(">#display>#Name") }
            val ptrNoMatch = shouldNotRaise { DataPointer.decodeFromString(">#display>#Lore") }

            pattern.match(ptrMatch) shouldBe false
            pattern.match(ptrNoMatch) shouldBe true
        }

        "RegexPattern compile - positive (negative=false)" {
            val pattern = CustomizedDataPointerPattern.RegexPattern(
                regex = """#lore>\d+(?:>#raw)?$""",
            ).compile()

            val ptr = shouldNotRaise { DataPointer.decodeFromString(">#components>#lore>5>#raw") }
            pattern.match(ptr) shouldBe true
        }

        "RegexPattern compile - negative flag inverts result" {
            val pattern = CustomizedDataPointerPattern.RegexPattern(
                regex = """#lore>\d+(?:>#raw)?$""",
                negative = true
            ).compile()

            val ptrMatch = shouldNotRaise { DataPointer.decodeFromString(">#components>#lore>5>#raw") }
            val ptrNoMatch = shouldNotRaise { DataPointer.decodeFromString(">#components>#custom_name") }

            pattern.match(ptrMatch) shouldBe false
            pattern.match(ptrNoMatch) shouldBe true
        }
    }
})
