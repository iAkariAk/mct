package mct.model.text

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import mct.util.formatir.*
import org.intellij.lang.annotations.Language

class TextComponentTest : FreeSpec({
    "Plain raw forms" - {
        "reads and preserves an IRString" {
            val raw = IRString("hello")
            val compound = TextComponent.Plain(raw)

            compound.text shouldBe "hello"
            compound.toIR() shouldBe raw
        }

        "reads and preserves an IRObject" {
            val raw = buildIRObject {
                put("type", "text")
                put("text", "hello")
                put("future_field", 42)
            }
            val compound = TextComponent.Plain(raw)

            compound.text shouldBe "hello"
            compound.toIR() shouldBe raw
        }

        "rejects every other raw type" {
            shouldThrow<IllegalArgumentException> {
                TextComponent.Plain(IRList())
            }
        }

        "promotes a modified IRString when object fields are needed" {
            val compound = TextComponent.Plain(IRString("hello"))
            compound.color = "red"

            compound.toIR() shouldBe buildIRObject {
                put("text", "hello")
                put("color", "red")
            }
        }

        "does not simplify away unmanaged raw fields" {
            val raw = buildIRObject {
                put("text", "hello")
                put("future_field", 42)
            }

            TextComponent.fromIR(raw).encodeToIR(simplify = true) shouldBe raw
        }

        "factory rejects non-component IR primitives" {
            shouldThrow<TextComponentCodecException> {
                TextComponent.fromIR(IRInt(42))
            }
        }

        "justify translate key" {
            listOf(
                "commands.teleport.success.location.single",
                "commands.title.show.title.single",
                "effect.minecraft.levitation",
            ).all(String::isTranslateKey).shouldBeTrue()
            listOf(
                "abc",
                "jelee",
                "Okey.",
                ".obj"
            ).none(String::isTranslateKey).shouldBeTrue()
        }
    }

    "object-backed component types" - {
        data class Case(
            val name: String,
            val raw: IRObject,
            val mutate: (TextComponent<*>) -> Unit,
            val changedKey: String,
            val changedValue: IRElement,
        )

        val cases = listOf(
            Case(
                "plain",
                buildIRObject { put("text", "before"); put("future_field", 42) },
                { (it as TextComponent.Plain).text = "after" },
                "text",
                IRString("after"),
            ),
            Case(
                "translatable",
                buildIRObject { put("translate", "before"); put("future_field", 42) },
                { (it as TextComponent.Translatable).translate = "after" },
                "translate",
                IRString("after"),
            ),
            Case(
                "keybind",
                buildIRObject { put("keybind", "before"); put("future_field", 42) },
                { (it as TextComponent.Keybind).keybind = "after" },
                "keybind",
                IRString("after"),
            ),
            Case(
                "score",
                buildIRObject {
                    put("score", buildIRObject {
                        put("name", "before")
                        put("objective", "objective")
                        put("future_score_field", 7)
                    })
                    put("future_field", 42)
                },
                { (it as TextComponent.Score).score = TextComponent.Score.Info("after", "objective") },
                "score",
                buildIRObject {
                    put("name", "after")
                    put("objective", "objective")
                    put("future_score_field", 7)
                },
            ),
            Case(
                "selector",
                buildIRObject { put("selector", "@a"); put("future_field", 42) },
                { (it as TextComponent.Selector).selector = "@p" },
                "selector",
                IRString("@p"),
            ),
            Case(
                "nbt",
                buildIRObject { put("nbt", "before"); put("entity", "@s"); put("future_field", 42) },
                { (it as TextComponent.Nbt).nbt = "after" },
                "nbt",
                IRString("after"),
            ),
            Case(
                "object",
                buildIRObject { put("object", "before"); put("future_field", 42) },
                { (it as TextComponent.Object).`object` = "after" },
                "object",
                IRString("after"),
            ),
            Case(
                "sprite",
                buildIRObject { put("sprite", "before"); put("future_field", 42) },
                { (it as TextComponent.Sprite).sprite = "after" },
                "sprite",
                IRString("after"),
            ),
        )

        cases.forEach { case ->
            case.name - {
                "preserves unknown fields while unchanged" {
                    val encoded = TextComponent.fromIR(case.raw).toIR() as IRObject

                    encoded["future_field"] shouldBe IRInt(42)
                }

                "encodes the edited field and preserves unknown fields" {
                    val compound = TextComponent.fromIR(case.raw)
                    case.mutate(compound)

                    val encoded = compound.toIR() as IRObject
                    encoded[case.changedKey] shouldBe case.changedValue
                    encoded["future_field"] shouldBe IRInt(42)
                }
            }
        }
    }

    "nested mutations" - {
        "propagates a mutation inside extra" {
            val raw = buildIRObject {
                put("translate", "message.key")
                put("extra", listOf(IRString("before")))
                put("future_field", 42)
            }
            val compound = TextComponent.fromIR(raw) as TextComponent.Translatable
            val extra = compound.extra as ManyTextComponent
            (extra.compounds.single() as TextComponent.Plain).text = "after"

            val encoded = compound.toIR()
            encoded["future_field"] shouldBe IRInt(42)
            encoded["extra"] shouldBe IRList(IRString("after"))
        }

        "propagates a mutation inside with" {
            val raw = buildIRObject {
                put("translate", "message.key")
                put("with", listOf(IRString("before")))
            }
            val compound = TextComponent.fromIR(raw) as TextComponent.Translatable
            (compound.with!!.single() as TextComponent.Plain).text = "after"

            compound.toIR()["with"] shouldBe IRList(IRString("after"))
        }

        "propagates a mutation inside separator" {
            val raw = buildIRObject {
                put("selector", "@a")
                put("separator", IRString("before"))
            }
            val compound = TextComponent.fromIR(raw) as TextComponent.Selector
            (compound.separator as TextComponent.Plain).text = "after"

            compound.toIR()["separator"] shouldBe IRString("after")
        }

        "propagates a mutation through an IRList" {
            val raw = IRList(IRString("before"), IRString("unchanged"))
            val compound = TextComponent.fromIR(raw) as ManyTextComponent
            (compound.compounds.first() as TextComponent.Plain).text = "after"

            compound.toIR() shouldBe IRList(
                IRString("after"),
                IRString("unchanged"),
            )
        }
    }

    "common fields" - {
        "accepts and normalizes legacy byte booleans" {
            val raw = buildIRObject {
                put("keybind", "key.use")
                put("italic", 0.toByte())
            }
            val compound = TextComponent.fromIR(raw) as TextComponent.Keybind

            compound.italic shouldBe false
            compound.toIR()["italic"] shouldBe IRBoolean(false)
        }

        "removes an optional field set to null" {
            val raw = buildIRObject {
                put("sprite", "minecraft:test")
                put("color", "red")
            }
            val compound = TextComponent.fromIR(raw) as TextComponent.Sprite
            compound.color = null

            compound.toIR().containsKey("color") shouldBe false
            raw["color"] shouldBe IRString("red")
        }
    }

    "TextComponent validation" - {
        fun shouldBeJsonTextComponent(@Language("json") json: String) {
            json.isTextComponentJson().shouldBeTrue()
        }

        fun shouldBeSnbtTextComponent(@Language("snbt") snbt: String) {
            snbt.isTextComponentSnbt().shouldBeTrue()
        }

        "recognizes nested compounds" {
            shouldBeJsonTextComponent("""[{"text":"a"},{"keybind":"key.use"}]""")
            shouldBeJsonTextComponent("""["A \' illegal escape"]""")
            shouldBeSnbtTextComponent("""[{text:"a"},{keybind:"key.use"}]""")
            shouldBeSnbtTextComponent("""["A \' illegal escape"]""")
            shouldBeSnbtTextComponent("""["",{color:"dark_red",text:"Warning!\n"},{color:"yellow",text:"This map is intended to be played in Survival mode only. Scouting in Spectator or Creative mode will spoil puzzles. I will not stop you, but this is not intended or required to solve this map."}]""")
        }
    }
})
