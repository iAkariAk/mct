package mct.dp

import io.kotest.assertions.fail
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import mct.DatapackExtraction
import mct.DatapackExtractionGroup
import mct.DatapackReplacement
import mct.Logger
import mct.dp.mcfunction.MCCommand
import mct.dp.mcfunction.extractTextMCF

class MCFunctionTest : StringSpec({
    fun parseMCFunction(mcf: String): List<MCCommand> {
        val logger = Logger.Console()
        return context(logger) {
            mct.dp.mcfunction.parseMCFunction(mcf)
        }
    }

    fun extractTextMCF(mcf: String): DatapackExtractionGroup {
        val logger = Logger.Console()
        return context(logger) {
            extractTextMCF(mcf, "test", "test")
        }
    }


    val TEST_MCF = $$"""
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
        val mcfunctions = parseMCFunction(TEST_MCF)
        withClue(mcfunctions) {
            mcfunctions.map { it.name } shouldBeEqual listOf(
                "tellraw", "tellraw", "complex", "tell", "say", "execute", "say"
            )
            mcfunctions.last().isMarco.shouldBeTrue()
        }
        mcfunctions.forEach {
            println(it)
        }
    }

    "test extract" {
        val result = extractTextMCF(TEST_MCF)
        println(result)
    }

    "test backfill" {
        val extraction = extractTextMCF(TEST_MCF).extractions
        val replacements = extraction.map {
            when (it) {
                is DatapackExtraction.MCFunction -> DatapackReplacement.MCFunction(it.indices, "{CIALLO}")
                is DatapackExtraction.MCJson -> fail("Should not reach")
            }
        }
        val backfilled = TEST_MCF.backfill(replacements)
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

})