package mct.util

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class Regex2Test : FreeSpec({
    "findAll advances after zero-length matches" {
        val matches = Regex2("(?=a)").findAll("a").take(3).toList()

        matches shouldHaveSize 1
        matches.single().range shouldBe (0..-1)
    }

    "findAll advances through zero-length matches away from the start and at the end" {
        Regex2("(?=a)|$").findAll("ba").map { it.range }.toList() shouldBe
            listOf(1..0, 2..1)
    }

    "unmatched capture groups preserve their indexes" {
        val match = Regex2("(?<a>a)?(?<b>b)").find("b")
        match shouldNotBe null

        match!!.groups.size shouldBe 3
        match.groups[0]?.value shouldBe "b"
        match.groups[1] shouldBe null
        match.groups[2]?.value shouldBe "b"
        match.groups[2]?.range shouldBe (0..0)
        match.groupValues shouldBe listOf("b", "", "b")

        match.groups2.size shouldBe 3
        match.groups2[1] shouldBe null
        match.groups2[2]?.value shouldBe "b"
        match.groups2[2]?.range shouldBe (0..0)
        match.groups2["a"] shouldBe null
        match.groups2["b"]?.value shouldBe "b"
        shouldThrow<IllegalArgumentException> { match.groups2["missing"] }
    }

    "matches and matchEntire require the whole input" {
        // `a|ab` is the discriminating case: taking the leftmost alternative reports `false`.
        Regex2("a|ab").matches("ab") shouldBe true
        Regex2("a|ab").matchEntire("ab")?.value shouldBe "ab"

        Regex2("a").matches("ab") shouldBe false
        Regex2("a").matchEntire("ab") shouldBe null

        Regex2(".*?x").matches("abx") shouldBe true

        // A `$` inside a MULTILINE pattern must not let a shorter alternative win.
        Regex2("a|a\n", RegexOption.MULTILINE).matches("a\n") shouldBe true
    }

    "replace expands group references like the JVM" {
        Regex2("(\\d+)-(\\d+)").replace("1-2 3-4", "${'$'}2:${'$'}1") shouldBe "2:1 4:3"
        Regex2("(?<a>a)?(?<b>b)").replace("b", "[${'$'}{a}:${'$'}{b}]") shouldBe "[:b]"
        Regex2("a.b").replace("a.b", "x${Regex2.escapeReplacement("${'$'}")}y") shouldBe "x${'$'}y"
        Regex2("a").replace("a", "\\${'$'}") shouldBe "${'$'}"

        shouldThrow<IndexOutOfBoundsException> { Regex2("a").replace("a", "${'$'}1") }
    }

    "replaceFirst replaces only the first match" {
        Regex2("a").replaceFirst("aaa", "b") shouldBe "baa"
    }

    "next uses the result's own cursor" {
        val regex = Regex2("a")
        val first = regex.find("a a")
        first shouldNotBe null

        regex.containsMatchIn("none") shouldBe false

        first!!.next()?.range shouldBe (2..2)
    }
})
