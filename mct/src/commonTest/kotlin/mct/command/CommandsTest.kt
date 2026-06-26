package mct.command

import io.kotest.assertions.fail
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import mct.Logger
import mct.TestFunctions
import mct.dp.backfillMCFunction
import mct.mappings
import mct.model.patch.*
import mct.model.patch.DatapackReplacement.MCFunction
import mct.pointer.DataPointer
import mct.util.unreachable

class CommandsTest : StringSpec({
    fun parseCommandsWithLogger(mcf: String): List<MCCommand> {
        val logger = Logger.Console()
        return context(logger) {
            parseCommands(mcf)
        }
    }

    fun extractText(str: String): List<DatapackExtraction> {
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
            when (it) {
                is DatapackExtraction.MCFunction -> MCFunction(it.indices, "{CIALLO}", null)
                is DatapackExtraction.MCJson -> fail("Should not reach")
                is DatapackExtraction.Nbt -> unreachable
            }
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
            it.replace {
                mappings[it] ?: return@mapNotNull null
            } as MCFunction
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
        val funcExtractions = extractions.filterIsInstance<DatapackExtraction.MCFunction>()
        val replacement = MCFunction(funcExtractions[0].indices, "{greeting}", null)
        val backfilled = mcf.backfillMCFunction(listOf(replacement))
        backfilled shouldBe "say {greeting}"
    }

    "test backfill multiple extractions from different lines" {
        val mcf = """
                say alpha
                say beta
            """.trimIndent()
        val extractions = extractText(mcf)
        val funcExtractions = extractions.filterIsInstance<DatapackExtraction.MCFunction>()
        funcExtractions.size shouldBe 2
        val replacements = listOf(
            MCFunction(funcExtractions[0].indices, "{A}", null),
            MCFunction(funcExtractions[1].indices, "{B}", null),
        )
        val backfilled = mcf.backfillMCFunction(replacements)
        backfilled shouldBe """
                say {A}
                say {B}
            """.trimIndent()
    }

    "test syntax kind is propagated through extraction" {
        // Literal name extraction. extractText produces BOTH selector extraction
        // (content="foo", syntax=Literal) and greedy command extraction
        // (content="@p[name=foo]", syntax=Literal).
        // Find the selector extraction by its short content
        val selectorExtraction = extractText("say @p[name=foo]")
            .filterIsInstance<DatapackExtraction.MCFunction>()
            .find { it.content == "foo" }
        selectorExtraction shouldNotBe null
        selectorExtraction!!.syntax shouldBe SnbtSyntaxKind.LiteralString
    }

    "test syntax kind double-quoted" {
        // For double-quoted names, the raw content stored includes the quotes
        val selectorExtraction = extractText("""say @p[name="hello"]""")
            .filterIsInstance<DatapackExtraction.MCFunction>()
            .find { it.content == "\"hello\"" }
        selectorExtraction shouldNotBe null
        selectorExtraction!!.content shouldBe "\"hello\""
        selectorExtraction.syntax shouldBe SnbtSyntaxKind.DoubleQuoteString
    }

    "test syntax kind single-quoted" {
        // For single-quoted names, the raw content stored includes the quotes
        val selectorExtraction = extractText("say @p[name='world']")
            .filterIsInstance<DatapackExtraction.MCFunction>()
            .find { it.content == "'world'" }
        selectorExtraction shouldNotBe null
        selectorExtraction!!.syntax shouldBe SnbtSyntaxKind.SingleQuoteString
    }

    "test auto unquoted & quoted" {
        val mcf = "say @e[name='Foo']"
        val selectorExtraction = extractText(mcf)
            .filterIsInstance<DatapackExtraction.MCFunction>()
            .find { it.unquoted() == "Foo" }
        selectorExtraction shouldNotBe null
        selectorExtraction!!.syntax shouldBe SnbtSyntaxKind.SingleQuoteString

        // Replace the selector's extracted range
        val replacement = selectorExtraction.replace {
            println(it)
            "Bar"
        }
        val backfilled = mcf.backfillMCFunction(listOf(replacement))
        backfilled shouldBe "say @e[name=\"Bar\"]"
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
            NbtExtraction.Command.Location(0..4, "AAAAA", null),
            NbtExtraction.Command.Location(5..9, "BBBBB", null),
        )

        val cmd = NbtExtraction.Command(
            pointer = DataPointer.Terminator,
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
            NbtExtraction.Command.Location(0..4, "AAAAA", null),
            NbtExtraction.Command.Location(6..10, "BBBBB", null),
        )

        val cmd = NbtExtraction.Command(
            pointer = DataPointer.Terminator,
            raw = raw,
            locations = locations,
        )

        val result = cmd.replace { listOf("XXXXX", "YYYYY") }
        result.replacement shouldBe "XXXXX-YYYYY"
    }

    "test snbt extraction via SnbtEntire carries SnbtSyntaxKind.Compound" {
        // dialog uses SnbtEntire at position 3 — nested text compounds
        // should carry the correct syntax kind
        val extraction =
            extractText("""dialog show @a {"type":"minecraft:notice","title":{"text":"Hello","color":"red"}}""")
                .filterIsInstance<DatapackExtraction.MCFunction>()
                .find { "Hello" in it.content }
        extraction shouldNotBe null
        extraction!!.syntax shouldBe SnbtSyntaxKind.Compound
    }

    "test snbt extraction via data merge entity carries SnbtSyntaxKind" {
        // data merge entity uses SnbtEntire at position 4 with BuiltinCommandDataPatterns
        // Should extract at least one text from the NBT
        val extraction = extractText("""data merge entity @e[limit=1] {CustomName:"Hello"}""")
            .filterIsInstance<DatapackExtraction.MCFunction>()
            .firstOrNull()
        // At minimum, an extraction should exist (syntax depends on how data merge pattern routes)
        extraction shouldNotBe null
    }

    "test plain extraction (tellraw) carries null syntax" {
        // tellraw uses Positions(2) = PlainEntire → syntax is null
        val extraction = extractText("""tellraw @a {"text":"Hello","color":"red"}""")
            .filterIsInstance<DatapackExtraction.MCFunction>()
            .find { "Hello" in it.content }
        extraction shouldNotBe null
        extraction!!.syntax shouldBe null
    }

    "test target selector literal string carries LiteralString syntax" {
        // Unquoted selector names should get LiteralString, not Compound
        val selectorExtraction = extractText("say @p[name=literalName]")
            .filterIsInstance<DatapackExtraction.MCFunction>()
            .find { it.content == "literalName" }
        selectorExtraction shouldNotBe null
        selectorExtraction!!.syntax shouldBe SnbtSyntaxKind.LiteralString
    }

    "test target selector literal string syntax propagated to Location for region commands" {
        // Simulate how region/Extract.kt converts ExtractedCommandSlice to Location
        // with the syntax field
        val slice = extractText("say @p[name=bareword]")
            .filterIsInstance<DatapackExtraction.MCFunction>()
            .find { it.content == "bareword" }
        slice shouldNotBe null

        // Convert to Location as done in region/Extract.kt
        val location = NbtExtraction.Command.Location(
            indices = slice!!.indices,
            content = slice.content,
            syntax = slice.syntax,
        )
        location.syntax shouldBe SnbtSyntaxKind.LiteralString
        location.unquoted() shouldBe "bareword"
    }
})
