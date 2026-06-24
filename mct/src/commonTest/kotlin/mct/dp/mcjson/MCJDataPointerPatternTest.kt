package mct.dp.mcjson

import arrow.core.partially2
import io.kotest.core.spec.style.FreeSpec
import mct.pointer.shouldMatch
import mct.pointer.shouldNotMatch

class MCJDataPointerPatternTest : FreeSpec({
    val shouldMatch = ::shouldMatch.partially2(BuiltinMCJPatterns)
    val shouldNotMatch = ::shouldNotMatch.partially2(BuiltinMCJPatterns)

    "BuiltinMCJPatterns" - {
        "match custom_name component" {
            shouldMatch(">#components>#custom_name")
        }

        "match lore component with index" {
            shouldMatch(">#components>#lore")
        }

        "match advancement title" {
            shouldMatch(">#display>#title")
        }

        "match advancement description" {
            shouldMatch(">#display>#description")
        }

        "match legacy item Name" {
            shouldMatch(">#display>#Name")
        }

        "match legacy item Lore" {
            shouldMatch(">#display>#Lore")
        }

        "match entity CustomName" {
            shouldMatch(">#CustomName")
        }

        "match sign front_text messages" {
            shouldMatch(">#front_text>#messages")
        }

        "match sign back_text messages" {
            shouldMatch(">#back_text>#messages")
        }

        "match book pages" {
            shouldMatch(">#pages")
        }

        "match book title" {
            shouldMatch(">#title")
        }

        "match book author" {
            shouldMatch(">#author")
        }

        "match loot table set_name text" {
            shouldMatch(">#functions>0>#name")
        }

        "match loot table set_lore text" {
            shouldMatch(">#functions>0>#lore")
        }


        "match loot table set_attributes modifier name text" {
            shouldMatch(">#functions>0>#modifiers>0>#name")
        }

        "match painting variant title text component" {
            shouldMatch(">#title")
        }

        "match painting variant author text component" {
            shouldMatch(">#author")
        }

        "not match painting variant title color leaf" {
            shouldNotMatch(">#title>#color")
        }

        "not match painting variant author color leaf" {
            shouldNotMatch(">#author>#color")
        }

        "not match unrelated path under title" {
            shouldNotMatch(">#title>#extra")
        }

        "not match unrelated path" {
            shouldNotMatch(">#random>#key")
        }

        "match description as plain string" {
            shouldMatch(">#description")
        }

        "match description as text component" {
            shouldMatch(">#description")
        }

        "match description color under display" {
            shouldMatch(">#display>#description")
        }
    }
})
