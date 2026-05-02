package mct.pointer

import io.kotest.assertions.arrow.core.shouldNotRaise
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import mct.dp.mcjson.BuiltinMCJPatterns
import mct.region.BuiltinRegionPatterns

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

    "BuiltinRegionPatterns" - {
        fun ptr(s: String) = shouldNotRaise { DataPointer.decodeFromString(s) }

        "match item display Name" {
            ptr(">#display>#Name").matches(BuiltinRegionPatterns) shouldBe true
        }

        "match item display Lore" {
            ptr(">#display>#Lore").matches(BuiltinRegionPatterns) shouldBe true
        }

        "match lore component with index" {
            ptr(">#components>#minecraft:lore>2").matches(BuiltinRegionPatterns) shouldBe true
        }

        "match lore component with raw" {
            ptr(">#components>#minecraft:lore>2>#raw").matches(BuiltinRegionPatterns) shouldBe true
        }

        "match written book pages" {
            ptr(">#components>#minecraft:written_book_content>#pages>0").matches(BuiltinRegionPatterns) shouldBe true
        }

        "match writable book pages" {
            ptr(">#components>#minecraft:writable_book_content>#pages>0>#raw").matches(BuiltinRegionPatterns) shouldBe true
        }

        "match entity CustomName" {
            ptr(">#>#Entities>0>#CustomName").matches(BuiltinRegionPatterns) shouldBe true
        }

        "match spawner entity CustomName" {
            ptr(">#>#SpawnData>#entity>#CustomName").matches(BuiltinRegionPatterns) shouldBe true
        }

        "match sign front_text messages" {
            ptr(">#>#block_entities>5>#front_text>#messages>0>#raw").matches(BuiltinRegionPatterns) shouldBe true
        }

        "match sign back_text filtered_messages" {
            ptr(">#>#block_entities>5>#back_text>#filtered_messages>3").matches(BuiltinRegionPatterns) shouldBe true
        }

        "match block entity CustomName" {
            ptr(">#>#block_entities>3>#CustomName").matches(BuiltinRegionPatterns) shouldBe true
        }

        "match book title in tag" {
            ptr(">#Book>#tag>#title").matches(BuiltinRegionPatterns) shouldBe true
        }

        "match book author in tag" {
            ptr(">#Book>#tag>#author").matches(BuiltinRegionPatterns) shouldBe true
        }

        "not match unrelated path" {
            ptr(">#unrelated>#path>#here").matches(BuiltinRegionPatterns) shouldBe false
        }
    }

    "BuiltinMCJPatterns" - {
        fun ptr(s: String) = shouldNotRaise { DataPointer.decodeFromString(s) }

        "match custom_name component" {
            ptr(">#components>#custom_name").matches(BuiltinMCJPatterns) shouldBe true
        }

        "match lore component with index" {
            ptr(">#components>#lore>0").matches(BuiltinMCJPatterns) shouldBe true
        }

        "match advancement title" {
            ptr(">#display>#title").matches(BuiltinMCJPatterns) shouldBe true
        }

        "match advancement description" {
            ptr(">#display>#description").matches(BuiltinMCJPatterns) shouldBe true
        }

        "match legacy item Name" {
            ptr(">#display>#Name").matches(BuiltinMCJPatterns) shouldBe true
        }

        "match legacy item Lore" {
            ptr(">#display>#Lore>0").matches(BuiltinMCJPatterns) shouldBe true
        }

        "match entity CustomName" {
            ptr(">#CustomName").matches(BuiltinMCJPatterns) shouldBe true
        }

        "match sign front_text messages" {
            ptr(">#front_text>#messages>0").matches(BuiltinMCJPatterns) shouldBe true
        }

        "match sign back_text messages" {
            ptr(">#back_text>#messages>3").matches(BuiltinMCJPatterns) shouldBe true
        }

        "match book pages" {
            ptr(">#pages>0").matches(BuiltinMCJPatterns) shouldBe true
        }

        "match book title" {
            ptr(">#title").matches(BuiltinMCJPatterns) shouldBe true
        }

        "match book author" {
            ptr(">#author").matches(BuiltinMCJPatterns) shouldBe true
        }

        "match loot table set_name text" {
            ptr(">#functions>0>#name>#text").matches(BuiltinMCJPatterns) shouldBe true
        }

        "match loot table set_lore fallback" {
            ptr(">#functions>0>#lore>0>#fallback").matches(BuiltinMCJPatterns) shouldBe true
        }

        "not match unrelated path" {
            ptr(">#random>#key").matches(BuiltinMCJPatterns) shouldBe false
        }
    }
})
