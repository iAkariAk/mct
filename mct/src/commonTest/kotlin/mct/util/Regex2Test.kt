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

    "next uses the result's own cursor" {
        val regex = Regex2("a")
        val first = regex.find("a a")
        first shouldNotBe null

        regex.containsMatchIn("none") shouldBe false

        first!!.next()?.range shouldBe (2..2)
    }
})
