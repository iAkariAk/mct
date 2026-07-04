package mct.mtl

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import mct.text.TextCompound
import mct.text.many

class TemplateGeneratorTest : FreeSpec({
    "TextCompound.mtlize()" - {
        "plain text should become a literal" {
            TextCompound.Plain("Hello World").mtlize() shouldBe MTLLiteral(null, "Hello World")
        }

        "plain text extras should become nested MTL pairs and lists" {
            TextCompound.Plain(
                text = "Outer",
                extra = listOf(
                    TextCompound.Plain(
                        text = "Inner",
                        extra = listOf(TextCompound.Plain("Deep"))
                    )
                )
            ).mtlize() shouldBe MTLPair(
                null,
                MTLLiteral(null, "Outer"),
                MTLList(
                    null,
                    listOf(
                        MTLPair(
                            null,
                            MTLLiteral(null, "Inner"),
                            MTLList(null, listOf(MTLLiteral(null, "Deep")))
                        )
                    )
                )
            )
        }

        "non-plain text should not be mtlized" {
            TextCompound.Translatable(translate = "item.name").mtlize().shouldBeNull()
        }
    }

    "TextCompoundOneOrMany.mtlize()" - {
        "many plain texts should become an MTL list" {
            listOf(
                TextCompound.Plain("A"),
                TextCompound.Plain("B", extra = listOf(TextCompound.Plain("B1"))),
            ).many().mtlize() shouldBe MTLList(
                null,
                listOf(
                    MTLLiteral(null, "A"),
                    MTLPair(
                        null,
                        MTLLiteral(null, "B"),
                        MTLList(null, listOf(MTLLiteral(null, "B1")))
                    )
                )
            )
        }

        "many with non-plain text should not be mtlized" {
            listOf(
                TextCompound.Plain("A"),
                TextCompound.Translatable("item.name"),
            ).many().mtlize().shouldBeNull()
        }
    }

    "String.tryDecodeAsTextCompound()" - {
        "invalid text compound should return null" {
            "\"".tryDecodeAsTextCompound().shouldBeNull()
        }
    }

    "generateMTLX" - {
        "empty input should render only separators" {
            val generated = emptyList<String>().generateMTLXTemplate().render()

            withClue(generated) {
                generated shouldBe """
                    ---mtl---
                    ---raw---

                """.trimIndent()
            }
        }

        "plain and raw inputs should render into their own sections" {
            // language="JSON"
            @Suppress("JsonStandardCompliance")
            val input = listOf(
                """{"text":"Hello"}""",
                """{"translate":"item.name"}""",
                "Raw line",
                """{"text":"World"}""",
            )
            val generated = input.generateMTLXTemplate().render()

            withClue(generated) {
                generated shouldBe """
                    ---mtl---
                    |Hello| ==> |TODO|

                    |World| ==> |TODO|

                    ---raw---
                    |{"translate":"item.name"}| ==> |TODO|
                    |Raw line| ==> |TODO|

                """.trimIndent()
            }
        }

        "top-level text array should render as a list template" {
            // language="JSON"
            val input = listOf(
                """["Hello","World"]""",
            )
            val generated = input.generateMTLXTemplate().render()

            withClue(generated) {
                generated shouldBe """
                    ---mtl---
                    [
                      |Hello|
                      |World|
                    ] ==> [
                      |TODO|
                      |TODO|
                    ]

                    ---raw---

                """.trimIndent()
            }
        }

        "nested TextCompound should be reinserted correctly" {
            // language="JSON"
            val input = listOf(
                """{"text":"JELEE","extra":["GRIL"]}""",
                """{"text": "JELLYFISH","extra":[{"text":"FISH","extra":["A", "B"]}]}"""
            )
            val generated = input.generateMTLXTemplate().render()

            withClue(generated) {
                generated shouldBe """
                    ---mtl---
                    (
                      |JELEE|
                      [
                        |GRIL|
                      ]
                    ) ==> (
                      |TODO|
                      [
                        |TODO|
                      ]
                    )

                    (
                      |JELLYFISH|
                      [
                        (
                          |FISH|
                          [
                            |A|
                            |B|
                          ]
                        )
                      ]
                    ) ==> (
                      |TODO|
                      [
                        (
                          |TODO|
                          [
                            |TODO|
                            |TODO|
                          ]
                        )
                      ]
                    )

                    ---raw---

                """.trimIndent()
            }
        }
    }
})
