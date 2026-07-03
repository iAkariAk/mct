@file:Suppress("UNCHECKED_CAST")

package mct.mtl

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import mct.text.TextCompound
import mct.text.TextCompoundOneOrMany
import mct.text.many
import mct.text.one

class TemplateGeneratorTest : FreeSpec({
    "TextCompound.mtlize()" - {
        "Plain text without extra should convert to MTLLiteral" {
            val result = TextCompound.Plain("Hello World").mtlize()
            result.shouldNotBeNull()
            result.shouldBeInstanceOf<MTLLiteral>()
            result.content shouldBe "Hello World"
            result.indices shouldBe null
        }

        "Plain text with single extra should convert to MTLPair with MTLList" {
            val plain = TextCompound.Plain(
                text = "Hello",
                extra = listOf(TextCompound.Plain("World"))
            )
            val result = plain.mtlize()

            result.shouldNotBeNull()
            result.shouldBeInstanceOf<MTLPair>()
            (result.left as MTLLiteral).content shouldBe "Hello"

            result.right.shouldBeInstanceOf<MTLList>()
            val list = result.right
            list.exprs.size shouldBe 1
            (list.exprs[0] as MTLLiteral).content shouldBe "World"
        }

        "Plain text with multiple extras should convert to MTLPair with multi-item list" {
            val plain = TextCompound.Plain(
                text = "A",
                extra = listOf(
                    TextCompound.Plain("B"),
                    TextCompound.Plain("C")
                )
            )
            val result = plain.mtlize()

            result.shouldNotBeNull()
            result.shouldBeInstanceOf<MTLPair>()
            (result.left as MTLLiteral).content shouldBe "A"

            val list = result.right as MTLList
            list.exprs.size shouldBe 2
            (list.exprs[0] as MTLLiteral).content shouldBe "B"
            (list.exprs[1] as MTLLiteral).content shouldBe "C"
        }

        "Plain text with nested extra should convert recursively" {
            val plain = TextCompound.Plain(
                text = "Outer",
                extra = listOf(
                    TextCompound.Plain("Inner", extra = listOf(TextCompound.Plain("Deep")))
                )
            )
            val result = plain.mtlize()

            result.shouldNotBeNull()
            result.shouldBeInstanceOf<MTLPair>()
            (result.left as MTLLiteral).content shouldBe "Outer"

            val innerExpr = (result.right as MTLList).exprs[0]
            innerExpr.shouldBeInstanceOf<MTLPair>()
            (innerExpr.left as MTLLiteral).content shouldBe "Inner"
            ((innerExpr.right as MTLList).exprs[0] as MTLLiteral).content shouldBe "Deep"
        }

        "Plain text with formatting properties still converts (formatting is ignored)" {
            val result = TextCompound.Plain(
                text = "Runic Catalyst",
                color = "aqua",
                italic = false
            ).mtlize()
            result.shouldNotBeNull()
            result.shouldBeInstanceOf<MTLLiteral>()
            result.content shouldBe "Runic Catalyst"
        }

        "Translatable text should return null" {
            TextCompound.Translatable(translate = "item.name").mtlize().shouldBeNull()
        }

        "Score text should return null" {
            TextCompound.Score(TextCompound.Score.Info("player", "score")).mtlize().shouldBeNull()
        }

        "Selector text should return null" {
            TextCompound.Selector(selector = "@p").mtlize().shouldBeNull()
        }

        "Keybind text should return null" {
            TextCompound.Keybind(keybind = "key.jump").mtlize().shouldBeNull()
        }

        "Nbt text should return null" {
            TextCompound.Nbt(nbt = "entity.CustomName").mtlize().shouldBeNull()
        }

        "Object text should return null" {
            TextCompound.Object(`object` = "entity").mtlize().shouldBeNull()
        }

        "Sprite text should return null" {
            TextCompound.Sprite(sprite = "icon").mtlize().shouldBeNull()
        }

        "Empty string Plain should convert to MTLLiteral with empty content" {
            val result = TextCompound.Plain("").mtlize()
            result.shouldNotBeNull()
            result.shouldBeInstanceOf<MTLLiteral>()
            result.content shouldBe ""
        }
    }

    "TextCompoundOneOrMany.mtlize()" - {
        "One(Plain) should delegate to Plain.mtlize() and return MTLLiteral" {
            val result = TextCompound.Plain("Hello").one().mtlize()
            result.shouldNotBeNull()
            result.shouldBeInstanceOf<MTLLiteral>()
            result.content shouldBe "Hello"
        }

        "One(Plain with extra) should delegate and return MTLPair" {
            val result = TextCompound.Plain("A", extra = listOf(TextCompound.Plain("B"))).one().mtlize()
            result.shouldNotBeNull()
            result.shouldBeInstanceOf<MTLPair>()
            result.left.let { (it as MTLLiteral).content shouldBe "A" }
        }

        "One(non-Plain) should return null" {
            TextCompound.Translatable("item.name").one().mtlize().shouldBeNull()
        }

        "Many of plain texts should convert to MTLList" {
            val many = listOf(
                TextCompound.Plain("A"),
                TextCompound.Plain("B"),
                TextCompound.Plain("C")
            ).many()
            val result = many.mtlize()

            result.shouldNotBeNull()
            result.shouldBeInstanceOf<MTLList>()
            result.exprs.size shouldBe 3
            (result.exprs[0] as MTLLiteral).content shouldBe "A"
            (result.exprs[1] as MTLLiteral).content shouldBe "B"
            (result.exprs[2] as MTLLiteral).content shouldBe "C"
        }

        "Many with a mix of plain and compound texts" {
            val many = listOf(
                TextCompound.Plain("A"),
                TextCompound.Plain("B", extra = listOf(TextCompound.Plain("B1"))),
            ).many()
            val result = many.mtlize()

            result.shouldNotBeNull()
            result.shouldBeInstanceOf<MTLList>()
            result.exprs.size shouldBe 2

            (result.exprs[0] as MTLLiteral).content shouldBe "A"
            (result.exprs[1] as MTLPair).left.let { (it as MTLLiteral).content shouldBe "B" }
        }

        "Many with non-Plain element should return null for entire list" {
            val many = listOf(
                TextCompound.Plain("A"),
                TextCompound.Translatable("item.name"),
            ).many()
            many.mtlize().shouldBeNull()
        }

        "Empty Many should produce empty MTLList" {
            val result = emptyList<TextCompound>().many().mtlize()
            result.shouldNotBeNull()
            result.shouldBeInstanceOf<MTLList>()
            result.exprs.size shouldBe 0
        }
    }

    "String.tryDecodeAsTextCompoound()" - {
        "JSON string literal should decode to Plain text" {
            val result = "\"Hello World\"".tryDecodeAsTextCompound()
            result.shouldNotBeNull()
            result.shouldBeInstanceOf<TextCompoundOneOrMany.One>()
            (result.value as TextCompound.Plain).text shouldBe "Hello World"
        }

        "JSON object with text field" {
            val result = """{"text":"Hello"}""".tryDecodeAsTextCompound()
            result.shouldNotBeNull()
            result.shouldBeInstanceOf<TextCompoundOneOrMany.One>()
            (result.value as TextCompound.Plain).text shouldBe "Hello"
        }

        "JSON with text and formatting properties" {
            val result = """{"text":"Runic Catalyst","color":"aqua","italic":false}""".tryDecodeAsTextCompound()
            result.shouldNotBeNull()
            result.shouldBeInstanceOf<TextCompoundOneOrMany.One>()
            val plain = result.value as TextCompound.Plain
            plain.text shouldBe "Runic Catalyst"
            plain.color shouldBe "aqua"
            plain.italic shouldBe false
        }

        "JSON array should decode to Many TextCompounds" {
            val result = """["Hello","World"]""".tryDecodeAsTextCompound()
            result.shouldNotBeNull()
            result.shouldBeInstanceOf<TextCompoundOneOrMany.Many>()
            result.value.size shouldBe 2
            (result.value[0] as TextCompound.Plain).text shouldBe "Hello"
            (result.value[1] as TextCompound.Plain).text shouldBe "World"
        }

        "JSON object with extra field should decode to Plain with extras" {
            val result = """{"text":"A","extra":[{"text":"B"},{"text":"C"}]}""".tryDecodeAsTextCompound()
            result.shouldNotBeNull()
            result.shouldBeInstanceOf<TextCompoundOneOrMany.One>()
            val plain = result.value as TextCompound.Plain
            plain.text shouldBe "A"
            plain.extra.size shouldBe 2
            (plain.extra[0] as TextCompound.Plain).text shouldBe "B"
            (plain.extra[1] as TextCompound.Plain).text shouldBe "C"
        }

        "JSON with translatable component" {
            val result = """{"translate":"item.name"}""".tryDecodeAsTextCompound()
            result.shouldNotBeNull()
            result.shouldBeInstanceOf<TextCompoundOneOrMany.One>()
            (result.value as TextCompound.Translatable).translate shouldBe "item.name"
        }

        "JSON array of text objects with formatting" {
            val result =
                """[{"text":"Hello","color":"dark_purple"},{"text":"World","color":"gold"}]""".tryDecodeAsTextCompound()
            result.shouldNotBeNull()
            result.shouldBeInstanceOf<TextCompoundOneOrMany.Many>()
            result.value.size shouldBe 2
            (result.value[0] as TextCompound.Plain).also {
                it.text shouldBe "Hello"
                it.color shouldBe "dark_purple"
            }
            (result.value[1] as TextCompound.Plain).also {
                it.text shouldBe "World"
                it.color shouldBe "gold"
            }
        }

        "Invalid string should return null" {
            "\"".tryDecodeAsTextCompound().shouldBeNull()
        }

        "Empty string should return null" {
            "".tryDecodeAsTextCompound().shouldBeNull()
        }
    }

    "generateMTLX" - {
        "all valid simple text objects should produce MTL section with identity mappings" {
            val result = listOf(
                """{"text":"Hello"}""",
                """{"text":"World"}""",
            ).generateMTLXTemplate().render()

            result shouldContain MTLX.SEPARATOR_MTL
            result shouldContain MTLX.SEPARATOR_RAW
            result shouldContain "|Hello| ==> |TODO|"
            result shouldContain "|World| ==> |TODO|"
        }

        "empty list should produce only separators" {
            val result = emptyList<String>().generateMTLXTemplate().render()

            result shouldContain MTLX.SEPARATOR_MTL
            result shouldContain MTLX.SEPARATOR_RAW
            // Only the two separator lines
            result.lines().filter { it.isNotBlank() }.size shouldBe 2
        }

        "valid JSON array input should produce MTL section with list mapping" {
            val result = listOf(
                """["Hello","World"]""",
            ).generateMTLXTemplate().render()

            result shouldContain MTLX.SEPARATOR_MTL
            // The JSON array decodes to Many([Plain("Hello"), Plain("World")])
            // mtlize() returns MTLList with [MTLLiteral("Hello"), MTLLiteral("World")]
            result shouldContain "["      // MTLList opening
            result shouldContain "|Hello|"
            result shouldContain "|World|"
            result shouldContain "]"      // MTLList closing
            // And it's an identity mapping: list ==> list
            result shouldContain " ==> "
        }

        "real-world-like inputs with formatting should produce MTL section" {
            val result = listOf(
                """{"text":"Runic Catalyst","color":"aqua","italic":false}""",
                """{"text":"Simple name"}"""
            ).generateMTLXTemplate().render()

            result shouldContain MTLX.SEPARATOR_MTL
            result shouldContain "|Runic Catalyst| ==> |TODO|"
            result shouldContain "|Simple name| ==> |TODO|"
        }

        "JSON array of text objects with formatting should produce MTL section" {
            val result = listOf(
                """[{"text":"Hello","color":"dark_purple"},{"text":"World","color":"gold"}]""",
            ).generateMTLXTemplate().render()

            result shouldContain MTLX.SEPARATOR_MTL
            result shouldContain "["
            result shouldContain "|Hello|"
            result shouldContain "|World|"
            result shouldContain "]"
        }

        "translatable input should go to raw section with TODO" {
            val result = listOf(
                """{"translate":"item.name"}""",
            ).generateMTLXTemplate().render()

            result shouldContain MTLX.SEPARATOR_MTL
            result shouldContain MTLX.SEPARATOR_RAW
            result shouldContain """|{"translate":"item.name"}| ==> |TODO|"""
        }

        "mixed plain and translatable should populate both sections" {
            val result = listOf(
                """{"text":"Hello"}""",
                """{"translate":"item.name"}""",
                """{"text":"World"}""",
            ).generateMTLXTemplate().render()

            result shouldContain MTLX.SEPARATOR_MTL
            result shouldContain MTLX.SEPARATOR_RAW
            result shouldContain "|Hello| ==> |TODO|"
            result shouldContain "|World| ==> |TODO|"
            // The translatable one should NOT be in MTL section
            result shouldNotContain "|item.name| ==> |TODO|"
        }

        "output should be parseable by MTLX.fromString for plain text data" {
            val input = listOf(
                """{"text":"Hello"}""",
                """{"text":"World"}""",
            )
            val generated = input.generateMTLXTemplate().render()

            // Round-trip: parse the generated MTLX back
            val parsed = MTLX.fromString(generated)
            parsed.mtlMappings.size shouldBe 2
            parsed.mtlMappings[0].let { mapping ->
                (mapping.left as MTLLiteral).content shouldBe "Hello"
                (mapping.right as MTLLiteral).content shouldBe "TODO"
            }
            parsed.mtlMappings[1].let { mapping ->
                (mapping.left as MTLLiteral).content shouldBe "World"
                (mapping.right as MTLLiteral).content shouldBe "TODO"
            }
            parsed.rawMappings.size shouldBe 0
        }

        "output for extra-containing input should be parseable" {
            val input = listOf(
                """{"text":"A","extra":[{"text":"B"}]}""",
            )
            val generated = input.generateMTLXTemplate().render()

            val parsed = MTLX.fromString(generated)
            parsed.mtlMappings.size shouldBe 1
            val mapping = parsed.mtlMappings.first()
            // Should be: (|A| [|B|]) ==> (|A| [|B|])
            mapping.left.shouldBeInstanceOf<MTLPair>()
            val pair = mapping.left
            (pair.left as MTLLiteral).content shouldBe "A"
            (pair.right as MTLList).exprs.let { exprs ->
                exprs.size shouldBe 1
                (exprs[0] as MTLLiteral).content shouldBe "B"
            }
        }
    }
})
