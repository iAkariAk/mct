@file:Suppress("FunctionName")

package mct.dp.mcfunction

import io.kotest.assertions.fail
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import mct.Logger

class ExtractPatternTest : FreeSpec({
    fun parseMCFunction(mcf: String): List<MCCommand> =
        context(Logger.None) { mct.dp.mcfunction.parseMCFunction(mcf) }

    /**
     * Creates a simple mock MCCommand for unit testing conditions.
     */
    fun cmd(
        name: String = "test",
        vararg args: String,
        raw: String = args.joinToString(" ", prefix = "$name "),
    ): MCCommand {
        val argList = args.mapIndexed { i, content ->
            val start = raw.indexOf(content)
            val end = start + content.length - 1
            MCCommand.Arg(
                relativeIndices = start..end,
                indices = start..end,
                content = content
            )
        }
        return MCCommand(
            raw = raw,
            name = name,
            indices = 0..raw.length,
            args = argList
        )
    }

    "PreCondition" - {
        "Any matches everything" {
            PreCondition.Companion.Any.matches(cmd("say", "hello")) shouldBe true
            PreCondition.Companion.Any.matches(cmd("give", "@p", "diamond")) shouldBe true
        }

        "WithSize - non-strict (upper bound)" {
            val cond = PreCondition.Companion.WithSize(2)
            // non-strict: size >= args.size (upper bound check)
            cond.matches(cmd("say", "hello")) shouldBe true   // 1 arg, 2 >= 1
            cond.matches(cmd("tell", "@p", "hi")) shouldBe true  // 2 args, 2 >= 2
            cond.matches(cmd("tell", "@p", "hi", "extra")) shouldBe false // 3 args, 2 < 3
        }

        "WithSize - strict (==)" {
            val cond = PreCondition.Companion.WithSize(2, strict = true)
            cond.matches(cmd("tell", "@p", "hi")) shouldBe true
            cond.matches(cmd("tell", "@p", "hi", "extra")) shouldBe false
            cond.matches(cmd("say", "hello")) shouldBe false
        }

        "Regex matches raw command" {
            val cond = PreCondition.Companion.Regex("""\{.*text.*}""")
            cond.matches(cmd("tellraw", "@a", """{"text":"hello"}""")) shouldBe true
            cond.matches(cmd("say", "plain text")) shouldBe false
        }

        "And requires all conditions" {
            val cond = PreCondition.Companion.And(
                listOf(
                    PreCondition.Companion.WithSize(2),
                    PreCondition.Companion.Regex("""\{""")
                )
            )
            cond.matches(cmd("tellraw", "@a", """{"text":"hi"}""")) shouldBe true // 2 args, has '{'
            cond.matches(cmd("tellraw", "@a")) shouldBe false  // 1 arg, no '{'
            cond.matches(cmd("say", "hello", "world")) shouldBe false // 2 args but no '{'
        }

        "Or requires any condition" {
            val cond = PreCondition.Companion.Or(
                listOf(
                    PreCondition.Companion.Regex("text"),
                    PreCondition.Companion.WithSize(1, strict = true),
                )
            )
            cond.matches(cmd("say", """{"text":"hi"}""")) shouldBe true  // matches regex (1 arg too)
            cond.matches(cmd("say", "hi")) shouldBe true  // matches WithSize(1)
            cond.matches(cmd("say", "hi", "extra")) shouldBe false // 2 args (not strict=1) and no regex match
        }

        "None requires zero conditions match" {
            val cond = PreCondition.Companion.None(
                listOf(
                    PreCondition.Companion.Regex("text"),
                    PreCondition.Companion.Regex("json"),
                )
            )
            cond.matches(cmd("say", "hello")) shouldBe true
            cond.matches(cmd("say", """{"text":"hi"}""")) shouldBe false
        }
    }

    "IndexSelector" - {
        "Greedy constructs with position" {
            val selector = IndexSelector.Greedy(2)
            selector.position shouldBe 2
        }

        "Greedy is not NonGreedy" {
            val selector: IndexSelector = IndexSelector.Greedy(0)
            (selector !is IndexSelector.NonGreedy) shouldBe true
        }

        "NonGreedy Special matches specific indices" {
            val selector = IndexSelector.NonGreedy.Companion.Special(listOf(1, 3))
            selector.matches(1) shouldBe true
            selector.matches(2) shouldBe false
            selector.matches(3) shouldBe true
        }

        "NonGreedy Range matches within range" {
            val selector = IndexSelector.NonGreedy.Companion.Range(2..4)
            selector.matches(1) shouldBe false
            selector.matches(2) shouldBe true
            selector.matches(3) shouldBe true
            selector.matches(4) shouldBe true
            selector.matches(5) shouldBe false
        }

        "NonGreedy Any matches all" {
            IndexSelector.NonGreedy.Companion.Any.matches(1) shouldBe true
            IndexSelector.NonGreedy.Companion.Any.matches(100) shouldBe true
        }

        "NonGreedy And requires all" {
            val selector = IndexSelector.NonGreedy.Companion.And(
                listOf(
                    IndexSelector.NonGreedy.Companion.Range(2..5),
                    IndexSelector.NonGreedy.Companion.Special(listOf(3)),
                )
            )
            selector.matches(3) shouldBe true   // in range AND in special
            selector.matches(4) shouldBe false  // in range but NOT in special
        }

        "NonGreedy Or requires any" {
            val selector = IndexSelector.NonGreedy.Companion.Or(
                listOf(
                    IndexSelector.NonGreedy.Companion.Special(listOf(1)),
                    IndexSelector.NonGreedy.Companion.Range(4..5),
                )
            )
            selector.matches(1) shouldBe true
            selector.matches(4) shouldBe true
            selector.matches(2) shouldBe false
        }

        "NonGreedy None requires zero" {
            val selector = IndexSelector.NonGreedy.Companion.None(
                listOf(
                    IndexSelector.NonGreedy.Companion.Range(1..3),
                    IndexSelector.NonGreedy.Companion.Special(listOf(5)),
                )
            )
            selector.matches(4) shouldBe true
            selector.matches(1) shouldBe false
            selector.matches(5) shouldBe false
        }
    }

    "PostCondition" - {
        fun mockCmd() = cmd(
            "tellraw", "@a",
            """{"text":"Hello world","color":"red"}""",
            """{"text":"extra text"}""",
        )

        "Any matches everything" {
            PostCondition.Companion.Any.matches(mockCmd(), mockCmd().args[0]) shouldBe true
        }

        "MatchRegex checks arg content" {
            val cond = PostCondition.Companion.MatchRegex("""\{.*text.*}""")
            cond.matches(mockCmd(), mockCmd().args[1]) shouldBe true   // JSON arg
            cond.matches(mockCmd(), mockCmd().args[0]) shouldBe false  // "@a"
        }

        "Contain checks substring" {
            val cond = PostCondition.Companion.Contain("text")
            cond.matches(mockCmd(), mockCmd().args[1]) shouldBe true
            cond.matches(mockCmd(), mockCmd().args[0]) shouldBe false
        }

        "Equal checks exact match" {
            val cond = PostCondition.Companion.Equal("@a")
            cond.matches(mockCmd(), mockCmd().args[0]) shouldBe true
            cond.matches(mockCmd(), mockCmd().args[1]) shouldBe false
        }

        "At delegates to nested PostCondition at position" {
            val cond = PostCondition.Companion.At(2, PostCondition.Companion.MatchRegex("""\{.*text.*}"""))
            // At(2, regex) checks command[2] (1-based). In this cmd, command[2] = {"text":"Hello world","color":"red"}
            // which matches the regex. But the 2nd arg passed to matches() is irrelevant for At.
            cond.matches(mockCmd(), mockCmd().args[0]) shouldBe true  // At checks cmd[2], not the passed arg
        }

        "And requires all PostConditions" {
            val cond = PostCondition.Companion.And(
                listOf(
                    PostCondition.Companion.Contain("text"),
                    PostCondition.Companion.MatchRegex("""\{.*}""")
                )
            )
            cond.matches(mockCmd(), mockCmd().args[1]) shouldBe true   // contains "text" AND matches regex
            cond.matches(mockCmd(), mockCmd().args[0]) shouldBe false  // "@a"
        }

        "Or requires any PostCondition" {
            val cond = PostCondition.Companion.Or(
                listOf(
                    PostCondition.Companion.Equal("@a"),
                    PostCondition.Companion.Contain("text"),
                )
            )
            cond.matches(mockCmd(), mockCmd().args[0]) shouldBe true   // equal "@a"
            cond.matches(mockCmd(), mockCmd().args[1]) shouldBe true   // contains "text"
        }

        "None requires zero PostConditions match" {
            val cond = PostCondition.Companion.None(
                listOf(
                    PostCondition.Companion.Contain("text"),
                    PostCondition.Companion.Equal("@a"),
                )
            )
            cond.matches(mockCmd(), mockCmd().args[0]) shouldBe false  // equal "@a"
            cond.matches(mockCmd(), mockCmd().args[1]) shouldBe false  // contains "text"
        }
    }

    "ExtractPattern" - {
        "Greedy pattern with pre and post conditions" {
            val pattern = ExtractPattern(
                command = "say",
                preCondition = PreCondition.Companion.Any,
                selected = IndexSelector.Greedy(0),
                postCondition = PostCondition.Companion.Any,
            )
            pattern.command shouldBe "say"
            pattern.preCondition.matches(cmd("say", "hi")) shouldBe true
            // Greedy selector is handled via when-branch, not via .matches()
            when (pattern.selected) {
                is IndexSelector.Greedy -> pattern.selected.position shouldBe 0
                is IndexSelector.NonGreedy -> error("Expected Greedy")
            }
        }

        "NonGreedy Special + MatchRegex combination" {
            val pattern = ExtractPattern(
                command = "tellraw",
                preCondition = PreCondition.Companion.WithSize(2),
                selected = IndexSelector.NonGreedy.Companion.Special(listOf(2)),
                postCondition = PostCondition.Companion.MatchRegex("""\{.*text.*}"""),
            )

            val validCmd = cmd(
                "tellraw", "@a",
                """{"text":"hello","color":"red"}"""
            )
            val tooManyArgsCmd = cmd("tellraw", "@a", "one", "two", "three")
            val wrongContentCmd = cmd("tellraw", "@a", "plain text")

            pattern.preCondition.matches(validCmd) shouldBe true
            pattern.preCondition.matches(tooManyArgsCmd) shouldBe false  // 4 args > WithSize(2)

            val selector = pattern.selected as IndexSelector.NonGreedy
            selector.matches(2) shouldBe true
            selector.matches(1) shouldBe false

            pattern.postCondition.matches(validCmd, validCmd.args[1]) shouldBe true
            pattern.postCondition.matches(wrongContentCmd, wrongContentCmd.args[1]) shouldBe false
        }
    }

    "BuiltinMCFPatterns" - {
        fun matchCmd(cmd: MCCommand) = extractTextFromCommand(BuiltinMCFPatterns, cmd)


        fun shouldMatches(mcf: String) {
            val cmds = parseMCFunction(mcf)
            val matches = cmds.flatMap(::matchCmd)
            if (matches.isEmpty())
                fail("No patterns matched for: $mcf")
        }

        "match say command" {
            shouldMatches("say Hello world everyone")
        }

        "match tellraw command" {
            shouldMatches("""tellraw @a {"text":"Hello","color":"red"}""")
        }

        "match title command" {
            shouldMatches("""title @a actionbar {"text":"Boss HP: 100"}""")
        }

        "match bossbar set name" {
            shouldMatches("""bossbar set mybar name {"text":"My Boss Bar"}""")
        }

        "match scoreboard objectives add" {
            shouldMatches("""scoreboard objectives add myobj dummy {"text":"My Objective"}""")
        }

        "match team modify prefix" {
            shouldMatches("""team modify myteam prefix {"text":"[VIP]"}""")
        }

        "match data modify set value" {
            // data modify entity @s <path> set value <json> (6 args, WithSize(6))
            val cmd = cmd(
                "data",
                "modify", "entity", "@s", "set", "value",
                """{"text":"Named Entity"}""",
            )
            val patterns = BuiltinMCFPatterns["data"].orEmpty()
            val p0 = patterns.first { (it.preCondition as? PreCondition.Companion.WithSize)?.size == 6 }
            p0.preCondition.matches(cmd) shouldBe true
            (p0.selected as IndexSelector.NonGreedy).matches(6) shouldBe true
            // Position 6 is the last arg: the JSON text component
            p0.postCondition.matches(cmd, cmd.args[5]) shouldBe true
        }

        "match give with text component" {
            // give <targets> <item> (2 args, WithSize(3), Positions(2))
            val cmd = cmd(
                "give",
                "@p",
                """stick{display:{Name:"text"}}""",
            )
            val patterns = BuiltinMCFPatterns["give"].orEmpty()
            patterns.isNotEmpty() shouldBe true
            val p = patterns.first()
            p.preCondition.matches(cmd) shouldBe true  // WithSize(3) >= 2
            (p.selected as IndexSelector.NonGreedy).matches(2) shouldBe true
        }

        "match item modify" {
            // item modify entity <target> <slot> <modifier> (5 args, WithSize(5))
            val cmd = cmd(
                "item",
                "modify", "entity", "@p", "weapon.mainhand",
                """{"function":"minecraft:set_name","name":"text"}""",
            )
            val patterns = BuiltinMCFPatterns["item"].orEmpty()
            patterns.isNotEmpty() shouldBe true
            val p = patterns.first()
            p.preCondition.matches(cmd) shouldBe true  // WithSize(5) >= 5
            (p.selected as IndexSelector.NonGreedy).matches(5) shouldBe true
        }
    }
})
