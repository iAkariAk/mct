package mct.mtl

import io.kotest.assertions.fail
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.booleans.shouldBeTrue
import mct.text.TextCompound
import mct.text.many
import mct.text.one

private inline fun displayWhenFailure(raw: String, block: () -> Unit) {
    try {
        block()
    } catch (e: MTLParseException) {
        val display = raw.replaceRange(e.position, "😰" + raw.substring(e.position) + "😰")
        withClue(display) {
            fail(e.stackTraceToString())
        }
    }
}

private fun MTLPaser.Companion.singleOf(mtl: String): MTLMapping = parse(mtl).first()


class MTLPaserTest : FreeSpec({
    "prototype" {
        val prototype = """
        |ITEM| ==> |A1|
        |ITEM2&|| ==> |A2|
        # comment
        | W  H  I  T  E  S  P  A  C  E   | ==> |  ...   |
        [
          |Text1|
          |Text2|
          |Text3|
          |Text4|
        ] ==> [
          |B1|
          |B2|
          |B3|
          |B4|
        ]
        
        (
          |A|
          [
            |Text1|
            |Text3|
            |Text4|
          ]
        ) ==> (
          |B|
          [
            |Text1B|
            |Text3B|
            |Text4B|
          ]
        )
    """.trimIndent()
        displayWhenFailure(prototype) {
//            val lexer = MTLLexer(prototype)
//            lexer.asSequence().forEach {
//                println(it)
//            }
            MTLPaser.parse(prototype).forEach {
                println(it)
            }
        }
    }

    "should match corrently" {
        TextCompound.Plain("MIMI").one().matches(
            MTLPaser.singleOf(
                """
            |MIMI| ==> |SHIINA|
        """.trimIndent()
            ).left
        ).shouldBeTrue()

        TextCompound.Plain("MIMI", extra = listOf(TextCompound.Plain("SHIINA"))).one().matches(
            MTLPaser.singleOf(
                """
                (
                  |MIMI|
                  [|SHIINA|]
                ) ==> (
                  |SHIINA|
                  [|MIMI|]
                )
            """.trimIndent()
            ).left
        ).shouldBeTrue()

        listOf(
            TextCompound.Plain("MIMI", extra = listOf(TextCompound.Plain("SHIINA"))),
            TextCompound.Plain("SHIINA", extra = listOf(TextCompound.Plain("MIMI"))),
        ).many().matches(
            MTLPaser.singleOf(
                """
                [
                    (
                      |MIMI|
                      [|SHIINA|]
                    )
                    (
                      |SHIINA|
                      [|MIMI|]
                    )
                ] ==> (
                  |SHIINA|
                  [|MIMI|]
                )
            """.trimIndent()
            ).left
        ).shouldBeTrue()
    }
})