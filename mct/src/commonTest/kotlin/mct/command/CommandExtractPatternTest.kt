@file:Suppress("FunctionName")

package mct.command

import io.kotest.assertions.arrow.core.shouldNotRaise
import io.kotest.assertions.fail
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import mct.Logger
import mct.model.patch.SnbtSyntaxKind

data class CommandPatternCase(
    val name: String,
    val mcf: String,
    val expectedContents: List<String>? = null,
)

fun commandCase(
    name: String,
    mcf: String,
    vararg expectedContents: String,
) = CommandPatternCase(
    name = name,
    mcf = mcf,
    expectedContents = expectedContents.toList().takeIf { it.isNotEmpty() },
)

private fun parseMCFunction(mcf: String): List<MCCommand> =
    context(Logger.None) { parseCommands(mcf) }


private fun matchCmd(cmd: MCCommand) = shouldNotRaise {
    context(Logger.Console()) {
        extractTextFromCommand(cmd)
    }
}

fun List<CommandPatternCase>.test() {
    val errors = mapNotNull { case ->
        runCatching {
            val cmds = parseMCFunction(case.mcf)
            val matches = cmds.flatMap(::matchCmd)
            if (matches.isEmpty()) {
                error("No patterns matched for: ${case.mcf}")
            }
            case.expectedContents?.let { expectedContents ->
                val actualContents = matches.map { it.content }
                if (actualContents != expectedContents) {
                    error("expected contents $expectedContents, but actual contents were $actualContents")
                }
            }
        }.exceptionOrNull()?.let { error ->
            "${case.name}: ${error.message ?: error.toString()}"
        }
    }

    if (errors.isNotEmpty()) {
        fail(
            buildString {
                appendLine("BuiltinCommandPatterns failures (${errors.size}/${size}):")
                errors.forEach { appendLine("- $it") }
            }
        )
    }
}


class CommandExtractPatternTest : FreeSpec({


    /**
     * Creates a simple mock MCCommand for unit testing conditions.
     */
    fun cmd(
        name: String = "test",
        vararg args: String,
        raw: String = args.joinToString(" ", prefix = "$name "),
    ): MCCommand {
        val argList = args.mapIndexed { _, content ->
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
            val selector = IndexSelector.NonGreedy(mapOf(1 to null, 3 to null))
            selector.matches(1) shouldBe true
            selector.matches(2) shouldBe false
            selector.matches(3) shouldBe true
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
                val pattern = CommandExtractPattern(
                    command = "say",
                    preCondition = PreCondition.Companion.Any,
                    selector = IndexSelector.Greedy(0),
                    postCondition = PostCondition.Companion.Any,
                )
                pattern.command shouldBe "say"
                pattern.preCondition.matches(cmd("say", "hi")) shouldBe true
                // Greedy selector is handled via when-branch, not via .matches()
                when (pattern.selector) {
                    is IndexSelector.Greedy -> pattern.selector.position shouldBe 0
                    is IndexSelector.NonGreedy -> error("Expected Greedy")
                }
            }

            "NonGreedy Special + MatchRegex combination" {
                val pattern = CommandExtractPattern(
                    command = "tellraw",
                    preCondition = PreCondition.Companion.WithSize(2),
                    selector = IndexSelector.NonGreedy(mapOf(2 to null)),
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

                val selector = pattern.selector as IndexSelector.NonGreedy
                selector.matches(2) shouldBe true
                selector.matches(1) shouldBe false

                pattern.postCondition.matches(validCmd, validCmd.args[1]) shouldBe true
                pattern.postCondition.matches(wrongContentCmd, wrongContentCmd.args[1]) shouldBe false
            }
        }

        "BuiltinCommandPatterns" - {
            "BuiltinSet" {
                listOf(
                    commandCase("say command", "say Hello world everyone", "Hello world everyone"),
                    commandCase(
                        "tellraw command",
                        """tellraw @a {"text":"Hello","color":"red"}""",
                        """{"text":"Hello","color":"red"}"""
                    ),
                    commandCase(
                        "title command",
                        """title @a actionbar {"text":"Boss HP: 100"}""",
                        """{"text":"Boss HP: 100"}"""
                    ),
                    commandCase(
                        "bossbar set name",
                        """bossbar set mybar name {"text":"My Boss Bar"}""",
                        """{"text":"My Boss Bar"}"""
                    ),
                    commandCase(
                        "scoreboard objectives add",
                        """scoreboard objectives add myobj dummy {"text":"My Objective"}""",
                        """{"text":"My Objective"}"""
                    ),
                    commandCase(
                        "team modify prefix",
                        """team modify myteam prefix {"text":"[VIP]"}""",
                        """{"text":"[VIP]"}"""
                    ),
                    commandCase(
                        "data modify set value",
                        """data modify entity @s set value {"text":"Named Entity"}""",
                        """{"text":"Named Entity"}"""
                    ),
                    commandCase("give with text component", """give @p stick{display:{Name:"text"}}"""),
                    commandCase(
                        "summon",
                        """summon block_display -106 3 -436 {NoGravity:1b,Glowing:1b,CustomNameVisible:0b,Tags:["wickedorb"],CustomName:{"bold":true,"color":"dark_purple","text":"彩叶"},glow_color_override:0,transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[3f,3f,3f]},block_state:{Name:"minecraft:crying_obsidian"}}""",
                        """{"bold":true,"color":"dark_purple","text":"彩叶"}"""
                    ),
                    commandCase(
                        "item modify",
                        """item modify entity @p weapon.mainhand {"function":"minecraft:set_name","name":"text"}""",
                    ),
                    commandCase(
                        "team add displayName",
                        """team add myteam {"text":"My Team"}""",
                        """{"text":"My Team"}"""
                    ),
                    commandCase(
                        "setblock with NBT data",
                        """setblock ~ ~ ~ minecraft:chest {CustomName:'{"text":"Treasure","color":"gold"}'}""",
                    ),
                    commandCase(
                        "data merge entity NBT",
                        """data merge entity @s {CustomName:'{"text":"Named Entity"}'}""",
                    ),
                    commandCase(
                        "data merge block NBT",
                        """data merge block ~ ~ ~ {CustomName:'{"text":"Block Name"}'}""",
                    ),
                ).test()
            }
        }
    }

    "TargetSelector" - {
        fun extractTargetSelector(mcf: String): List<ExtractedCommandSlice> {
            val cmds = parseMCFunction(mcf)
            return cmds.flatMap { CommandExtractorIntrinsic.extractFromTargetSelector(it.args) }
        }

        "extracts literal name" {
            val slices = extractTargetSelector("say @p[name=foo]")
            slices.size shouldBe 1
            slices[0].content shouldBe "foo"
            slices[0].syntax shouldBe SnbtSyntaxKind.LiteralString
        }

        "extracts name with colon (namespaced)" {
            val slices = extractTargetSelector("say @p[name=foo:bar]")
            slices.size shouldBe 1
            slices[0].content shouldBe "foo:bar"
            slices[0].syntax shouldBe SnbtSyntaxKind.LiteralString
        }

        "extracts double-quoted name with quotes preserved in content" {
            // content stores the raw value including surrounding quotes
            val slices = extractTargetSelector("""say @p[name="hello"]""")
            slices.size shouldBe 1
            slices[0].content shouldBe "\"hello\""
            slices[0].syntax shouldBe SnbtSyntaxKind.DoubleQuoteString
        }

        "extracts single-quoted name with quotes preserved in content" {
            val slices = extractTargetSelector("say @p[name='hello']")
            slices.size shouldBe 1
            slices[0].content shouldBe "'hello'"
            slices[0].syntax shouldBe SnbtSyntaxKind.SingleQuoteString
        }

        "extracts negative name" {
            val slices = extractTargetSelector("say @p[name=!foo]")
            slices.size shouldBe 1
            slices[0].content shouldBe "foo"
            slices[0].syntax shouldBe SnbtSyntaxKind.LiteralString
        }

        "extracts name when selector has multiple properties" {
            val slices = extractTargetSelector("""say @p[type=minecraft:player,name="foo",tag=bar]""")
            slices.size shouldBe 1
            slices[0].content shouldBe "\"foo\""
            slices[0].syntax shouldBe SnbtSyntaxKind.DoubleQuoteString
        }

        "extracts name from last property before closing bracket" {
            val slices = extractTargetSelector("say @e[tag=carried,name=Foo]")
            slices.size shouldBe 1
            slices[0].content shouldBe "Foo"
            slices[0].syntax shouldBe SnbtSyntaxKind.LiteralString
        }

        "does not extract when no name argument" {
            val slices = extractTargetSelector("say @p")
            slices.size shouldBe 0
        }

        "does not extract from non-selector args" {
            val slices = extractTargetSelector("say hello world")
            slices.size shouldBe 0
        }

        "extracts from multiple selectors in one command" {
            val slices = extractTargetSelector("""say @p[name="foo"] @s[name=bar]""")
            slices.size shouldBe 2
            slices[0].content shouldBe "\"foo\""
            slices[0].syntax shouldBe SnbtSyntaxKind.DoubleQuoteString
            slices[1].content shouldBe "bar"
            slices[1].syntax shouldBe SnbtSyntaxKind.LiteralString
        }

        "indices slice back to the exact name value in the original string" {
            // For literal name, indices cover just the value (without trailing bracket)
            val raw = "say @p[name=foo]"
            val slices = extractTargetSelector(raw)
            slices.size shouldBe 1
            val slice = slices[0]
            slice.content shouldBe "foo"
            raw.substring(slice.indices) shouldBe "foo"
        }

        "indices for double-quoted name include the quotes" {
            // For double-quoted name, indices cover the quoted value (without trailing bracket)
            val raw = "say @p[name=\"foo\"]"
            val slices = extractTargetSelector(raw)
            slices.size shouldBe 1
            val slice = slices[0]
            slice.content shouldBe "\"foo\""
            raw.substring(slice.indices) shouldBe "\"foo\""
        }

        "indices for single-quoted name include the quotes" {
            val raw = "say @p[name='foo']"
            val slices = extractTargetSelector(raw)
            slices.size shouldBe 1
            val slice = slices[0]
            slice.content shouldBe "'foo'"
            raw.substring(slice.indices) shouldBe "'foo'"
        }
    }

    "RecursiveSubcommand" - {
        fun shouldMatches(mcf: String, vararg expectedContents: String) {
            val cmds = parseMCFunction(mcf)
            val matches = cmds.flatMap {
                shouldNotRaise {
                    context(Logger.Console()) {
                        extractTextFromCommand(it)
                    }
                }
            }
            matches.isNotEmpty() shouldBe true
            if (expectedContents.isNotEmpty()) {
                matches.map { it.content } shouldBe expectedContents.toList()
            }
        }

        "execute as @p run say" {
            shouldMatches("execute as @p run say Hello world", "Hello world")
        }

        "execute at @e run say" {
            shouldMatches("execute at @e run say Hello world", "Hello world")
        }

        "return run say" {
            shouldMatches("return run say Hello world", "Hello world")
        }

        "execute as @p run tellraw" {
            shouldMatches(
                """execute as @p run tellraw @a {"text":"Hello","color":"red"}""",
                """{"text":"Hello","color":"red"}"""
            )
        }

        "execute ... run execute ... run nested deep" {
            shouldMatches(
                "execute as @p run execute at @e run say deep hello",
                "deep hello"
            )
        }

        "execute run say with greedy extraction gets full remainder" {
            shouldMatches(
                "execute as @p run say hello world foo bar",
                "hello world foo bar"
            )
        }
    }

    "GreedyRange" - {
        fun shouldMatches(mcf: String, vararg expectedContents: String) {
            val cmds = parseMCFunction(mcf)
            val matches = cmds.flatMap {
                shouldNotRaise {
                    context(Logger.Console()) {
                        extractTextFromCommand(it)
                    }
                }
            }
            matches.isNotEmpty() shouldBe true
            if (expectedContents.isNotEmpty()) {
                matches.map { it.content } shouldBe expectedContents.toList()
            }
        }

        "greedy from position 0 extracts all text after command name" {
            shouldMatches("say hello world", "hello world")
        }

        "greedy from position 0 with no args uses full name offset" {
            // This tests the case where command.name.length == command.raw.length
            // e.g., the greedy selector tries to read beyond the command name
        }

        "greedy from position 0 with single word arg" {
            shouldMatches("say hello", "hello")
        }
    }
})
