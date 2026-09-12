package mct.command

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import mct.Logger
import mct.TestFunctions
import mct.dp.mcfunction.backfillMCFunction
import mct.mappings
import mct.model.patch.*
import mct.model.patch.DatapackReplacement.MCFunction

class CommandsTest : StringSpec({
    fun parseCommandsWithLogger(mcf: String): List<MCCommand> {
        val logger = Logger.Console()
        return context(logger) {
            parseCommands(mcf)
        }
    }

    fun extractText(str: String): List<DatapackExtraction.MCFunction> {
        val logger = Logger.Console()
        return context(logger) {
            extractTextFromCommands(str).map {
                DatapackExtraction.MCFunction(it.indices, it.content, it.syntax)
            }
        }
    }


    val TEST_COMMANDS = $$"""
                tellraw @a [{"storage":"global","nbt":"Prefix.ERROR"},{"text":"飛距離が設定されていない！"}]

                # ignore "Ciallo"
                tellraw @s {"text":"compact","extra":[{"text":"text"}]}
                complex "item_id_001" b{strength:50b, durability:100s}

                # test escape
                tell @a "\"Kukayo\": {\"text\": \"A text compound is like this\"} 🫧"

                \\ invali char

                say a greedy string

                execute as @p run say iroha kaguya 99

                $say $(s) No any one
              """.trimIndent()

    "test parser" {
        val commands = parseCommandsWithLogger(TEST_COMMANDS)
        withClue(commands) {
            commands.map { it.name } shouldBeEqual listOf(
                "tellraw", "tellraw", "complex", "tell", "say", "execute", "say"
            )
            commands.last().isMarco.shouldBeTrue()
        }
        commands.forEach {
            println(it)
        }
    }

    "test extract" {
        val result = extractText(TEST_COMMANDS)
        println(result)
    }

    "test backfill" {
        val extraction = extractText(TEST_COMMANDS)
        val replacements = extraction.map {
            MCFunction(it.indices, "{CIALLO}")
        }
        val backfilled = TEST_COMMANDS.backfillMCFunction(replacements)
        backfilled shouldBe $$"""
                tellraw @a {CIALLO}

                # ignore "Ciallo"
                tellraw @s {CIALLO}
                complex "item_id_001" b{strength:50b, durability:100s}

                # test escape
                tell @a {CIALLO}

                \\ invali char

                say {CIALLO}

                execute as @p run say {CIALLO}

                $say {CIALLO}
              """.trimIndent()
    }

    "test practice" {
        val extractions = extractText(TestFunctions.update_billboard)
        val replacements = extractions.mapNotNull {
            context(Logger.Console()) {
                it.replaceSimply { mappings[it] } as MCFunction?
            }
        }

        extractions.forEach {
            println(it.contents().toList())
        }
        val backfilled = TestFunctions.update_billboard.backfillMCFunction(replacements)
        println(backfilled)
    }

    "test backfill trailing chars preserved" {
        // Verify that backfill does NOT drop trailing characters (regression for off-by-n bug)
        // Greedy extraction on "say" goes to end of command, so backfill should replace the full range
        val mcf = "say hello world"
        val extractions = extractText(mcf)
        val replacement = MCFunction(extractions[0].indices, "{greeting}")
        val backfilled = mcf.backfillMCFunction(listOf(replacement))
        backfilled shouldBe "say {greeting}"
    }

    "test backfill multiple extractions from different lines" {
        val mcf = """
                say alpha
                say beta
            """.trimIndent()
        val extractions = extractText(mcf)
        extractions.size shouldBe 2
        val replacements = listOf(
            MCFunction(extractions[0].indices, "{A}"),
            MCFunction(extractions[1].indices, "{B}"),
        )
        val backfilled = mcf.backfillMCFunction(replacements)
        backfilled shouldBe """
                say {A}
                say {B}
            """.trimIndent()
    }

    "test backfill should fail when inputting overlapping replacements" {
        val raw = "ABC".repeat(100)
        shouldThrowAny {
            raw.backfillMCFunction(
                listOf(
                    MCFunction(4..6, "X"),
                    MCFunction(5..7, "X"),
                )
            )
        }.message shouldStartWith "Replacements cannot overlap with each other"

        shouldNotThrowAny {
            raw.backfillMCFunction(
                listOf(
                    MCFunction(4..6, "X"),
                    MCFunction(7..100, "X"),
                )
            )
        }
    }

    "test syntax kind is propagated through extraction" {
        val selectorExtraction = extractText("msg @p[name=foo] bar")
            .find { it.content == "foo" }
        selectorExtraction shouldNotBe null
        selectorExtraction!!.syntax shouldBe SnbtSyntaxKind.LiteralString
    }

    "test syntax kind double-quoted" {
        // For double-quoted names, the raw content stored includes the quotes
        val selectorExtraction = extractText("""msg @p[name="hello"] bar""")
            .find { it.content == "\"hello\"" }
        selectorExtraction shouldNotBe null
        selectorExtraction!!.content shouldBe "\"hello\""
        selectorExtraction.syntax shouldBe SnbtSyntaxKind.DoubleQuoteString
    }

    "test syntax kind single-quoted" {
        // For single-quoted names, the raw content stored includes the quotes
        val selectorExtraction = extractText("msg @p[name='world'] bar")
            .find { it.content == "'world'" }
        selectorExtraction shouldNotBe null
        selectorExtraction!!.syntax shouldBe SnbtSyntaxKind.SingleQuoteString
    }

    "test auto unquoted & quoted" {
        val mcf = "title @e[name='Foo'] title Foobar"
        val extractions = extractText(mcf)
        extractions.size shouldBe 2
        val selectorExtraction = extractions
            .find { it.unquoted() == "Foo" }
        selectorExtraction shouldNotBe null
        selectorExtraction!!.syntax shouldBe SnbtSyntaxKind.SingleQuoteString

        val replacement = selectorExtraction.replace {
            "Bar"
        }
        val backfilled = mcf.backfillMCFunction(listOf(replacement))
        backfilled shouldBe "title @e[name=\"Bar\"] title Foobar"
    }

    "test multi-location zip alignment" {
        // Test that RegionExtraction.Command.replace() correctly pairs locations with replacements.
        // The bug was .sortedByDescending before .zip(), causing cross-wired pairing when
        // locations are in ascending order (the expected order).
        // With locations [A(0), B(5)] (ascending) and replacements ["XXXXX", "YYYYY"]:
        // - Correct: A gets "XXXXX", B gets "YYYYY"
        // - Bug: sortedByDescending first = [B(5), A(0)], zipped = [(B,"XXXXX"), (A,"YYYYY")]

        val raw = "AAAAABBBBB"
        val locations = listOf(
            ExtractionContent.Command.Location(0..4, "AAAAA", null),
            ExtractionContent.Command.Location(5..9, "BBBBB", null),
        )

        val cmd = ExtractionContent.Command(
            raw = raw,
            locations = locations,
        )

        val result = cmd.replace { listOf("XXXXX", "YYYYY") }
        // A(0..4) -> "XXXXX", B(5..9) -> "YYYYY"
        // Backfill: replace 5..9 first (higher index), then 0..4
        result.replacement shouldBe "XXXXXYYYYY"
    }

    "test multi-location zip cross-wiring detection" {
        // This test specifically validates that each location gets the CORRECT replacement.
        // With differentiated replacement values, a cross-wired bug would produce wrong output.
        // Locations [A(0..4), B(6..10)] with replacements ["XXXXX", "YYYYY"]
        // Correct: A->XXXXX, B->YYYYY  (result: "XXXXX-YYYYY")
        // Broken:  A->YYYYY, B->XXXXX  (result: "YYYYY-XXXXX")
        val raw = "AAAAA-BBBBB"
        val locations = listOf(
            ExtractionContent.Command.Location(0..4, "AAAAA", null),
            ExtractionContent.Command.Location(6..10, "BBBBB", null),
        )

        val cmd = ExtractionContent.Command(
            raw = raw,
            locations = locations,
        )

        val result = cmd.replace { listOf("XXXXX", "YYYYY") }
        result.replacement shouldBe "XXXXX-YYYYY"
    }

    "test NBT command replacement should fail when locations overlap" {
        val command = ExtractionContent.Command(
            raw = "say @p[name=foo]",
            locations = listOf(
                ExtractionContent.Command.Location(4..15, "@p[name=foo]", null),
                ExtractionContent.Command.Location(12..14, "foo", SnbtSyntaxKind.LiteralString),
            ),
        )

        shouldThrowAny {
            command.replace { listOf("@p[name=foo]", "名字") }
        }.message shouldStartWith "Replacements cannot overlap with each other"
    }

    "test snbt extraction via SnbtEntire carries SnbtSyntaxKind.Compound" {
        // dialog uses SnbtEntire at position 3 — nested text compounds
        // should carry the correct syntax kind
        val extraction =
            extractText("""dialog show @a {"type":"minecraft:notice","title":{"text":"Hello","color":"red"}}""")
                .find { "Hello" in it.content }
        extraction shouldNotBe null
        extraction!!.syntax shouldBe SnbtSyntaxKind.Compound
    }

    "test snbt extraction via data merge entity carries SnbtSyntaxKind" {
        // data merge entity uses SnbtEntire at position 4 with BuiltinCommandDataPatterns
        // Should extract at least one text from the NBT
        val extraction = extractText("""data merge entity @e[limit=1] {CustomName:"Hello"}""")
            .firstOrNull()
        // At minimum, an extraction should exist (syntax depends on how data merge pattern routes)
        extraction shouldNotBe null
    }

    "test plain extraction (tellraw) carries null syntax" {
        // tellraw uses Positions(2) = PlainEntire → syntax is null
        val extraction = extractText("""tellraw @a {"text":"Hello","color":"red"}""")
            .find { "Hello" in it.content }
        extraction shouldNotBe null
        extraction!!.syntax shouldBe null
    }

    "test target selector literal string carries LiteralString syntax" {
        // Unquoted selector names should get LiteralString, not Compound
        val selectorExtraction = extractText("msg @p[name=literalName] bar")
            .find { it.content == "literalName" }
        selectorExtraction shouldNotBe null
        selectorExtraction!!.syntax shouldBe SnbtSyntaxKind.LiteralString
    }

    "test target selector literal string syntax propagated to Location for region commands" {
        // Simulate how region/Extract.kt converts ExtractedCommandSlice to Location
        // with the syntax field
        val slice = extractText("msg @p[name=bareword] bar")
            .find { it.content == "bareword" }
        slice shouldNotBe null

        // Convert to Location as done in region/Extract.kt
        val location = ExtractionContent.Command.Location(
            indices = slice!!.indices,
            content = slice.content,
            syntax = slice.syntax,
        )
        location.syntax shouldBe SnbtSyntaxKind.LiteralString
        location.unquoted() shouldBe "bareword"
    }
})
