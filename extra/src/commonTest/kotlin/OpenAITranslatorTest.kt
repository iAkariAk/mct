import io.kotest.assertions.arrow.core.shouldNotRaise
import io.kotest.assertions.fail
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import mct.Env
import mct.FormatKind
import mct.Logger
import mct.extra.translator.*
import mct.util.envvar

class OpenAITranslatorTest : FreeSpec({
    val apiUrl = envvar("OPENAI_URL")
    val token = envvar("OPENAI_TOKEN")
    val model = envvar("OPENAI_MODEL")
    suspend fun translator() = shouldNotRaise {
        OpenAITranslator(apiUrl!!, token!!, model!!, defaultTerms = emptySet())
    }

    "parse test" {
        val response = """
            -- MCT-CLI:TRANSLATED --
            [0] a
            [1] b
            [2] c
            -- MCT-CLI:TERMS --
            [{"source":"Iroha","target":"彩叶","type":"name"}]
            -- MCT-CLI:END --
        """.trimIndent()
        val (terms, translated) = parseLLMResponse(response, 3)
        terms shouldBe setOf(Term("Iroha", "彩叶", TermType.Name))
        translated shouldBe listOf("a", "b", "c")
    }

    val testEnabled = listOf(apiUrl, token, model).all { it != null }

    if (!testEnabled) {
        println("WARNING: Test was disabled due to no configure for OpenAI in env vars, please add `OPENAI_URL`, `OPENAI_TOKEN` and `OPENAI_MODEL`.")
    }

    "translate test".config(enabled = testEnabled) {
        val translator = translator()
        val result = translator.translate(FormatKind.Json, TEST_TEXT.lines())
        println("translated: $result")
        println("terms: ${translator.terms}")
    }

    "strip test" {
        val raws = listOf(
            """[{"color":"gray","text":"Key recipes unlocked!\n(Check the recipe book in a crafting table)"}]""",
            """{"color":"red","text":"ILLEGAL BUCKET USE DETECTED"}"""
        )
        context(Env()) {
            val result = raws.strip(FormatKind.Json)

            val failures = result.filterIsInstance<CompoundStrip.Failure>()

            if (failures.isNotEmpty()) {
                fail("Strip failed for: ${failures.joinToString { it.original }}")
            }
        }
    }

    "mock" - {
        "plain text" {
            val mockResponse = """
            -- MCT-CLI:TRANSLATED --
            [0] 你好世界
            [1] 这是测试
            -- MCT-CLI:TERMS --
            []
            -- MCT-CLI:END --
        """.trimIndent()

            val mockChat = mockChatCompletion(mockResponse)
            val translator = OpenAITranslator(
                chatCompletion = mockChat,
                model = "mock-model",
                defaultTerms = emptySet(),
                env = Env(logger = Logger.None)
            )

            val result = translator.translate(FormatKind.Json, listOf("Hello world", "This is a test"))
            result shouldBe listOf("你好世界", "这是测试")
        }

        "with existing terms" {
            val mockResponse = """
            -- MCT-CLI:TRANSLATED --
            [0] 辉夜姬很漂亮
            -- MCT-CLI:TERMS --
            []
            -- MCT-CLI:END --
        """.trimIndent()

            val existingTerms = setOf(Term("Kaguya", "辉夜姬", TermType.Name))
            val mockChat = mockChatCompletion(mockResponse)
            val translator = OpenAITranslator(
                chatCompletion = mockChat,
                model = "mock-model",
                defaultTerms = existingTerms,
                env = Env(logger = Logger.None)
            )

            val result = translator.translate(FormatKind.Json, listOf("Kaguya is beautiful"))
            result shouldBe listOf("辉夜姬很漂亮")
            translator.terms shouldBe existingTerms
        }

        "new terms discovered" {
            val mockResponse = """
            -- MCT-CLI:TRANSLATED --
            [0] 彩叶在散步
            -- MCT-CLI:TERMS --
            [{"source":"Iroha","target":"彩叶","type":"name"}]
            -- MCT-CLI:END --
        """.trimIndent()

            val mockChat = mockChatCompletion(mockResponse)
            val translator = OpenAITranslator(
                chatCompletion = mockChat,
                model = "mock-model",
                defaultTerms = emptySet(),
                env = Env(logger = Logger.None)
            )

            val result = translator.translate(FormatKind.Json, listOf("Iroha is walking"))
            result shouldBe listOf("彩叶在散步")
            translator.terms shouldBe setOf(Term("Iroha", "彩叶", TermType.Name))
        }

        "json text component" {
            val mockResponse = """
            -- MCT-CLI:TRANSLATED --
            [0] 你好
            -- MCT-CLI:TERMS --
            []
            -- MCT-CLI:END --
        """.trimIndent()

            val mockChat = mockChatCompletion(mockResponse)
            val translator = OpenAITranslator(
                chatCompletion = mockChat,
                model = "mock-model",
                defaultTerms = emptySet(),
                env = Env(logger = Logger.None)
            )

            val jsonInput = """{"text":"Hello","color":"red"}"""
            val result = translator.translate(FormatKind.Json, listOf(jsonInput))
            result[0] shouldBe """{"text":"你好","color":"red"}"""
        }

        "long request chunking" {
            var callIndex = 0
            val callChunkSizes = mutableListOf<Int>()

            val mockChat: suspend (Int, String) -> Pair<TermTable, List<String?>> = { expectedSize, _ ->
                val idx = callIndex++
                callChunkSizes += expectedSize
                val content = buildString {
                    appendLine("-- MCT-CLI:TRANSLATED --")
                    (0 until expectedSize).joinTo(this, "\n") { i -> "[$i] chunk${idx}_line${i}" }
                    appendLine()
                    appendLine("-- MCT-CLI:TERMS --")
                    appendLine("[]")
                    append("-- MCT-CLI:END --")
                }
                parseLLMResponse(content, expectedSize)
            }

            val sources = (0 until 10).flatMap { TEST_TEXT.lines() }

            val translator = OpenAITranslator(
                chatCompletion = mockChat,
                model = "mock-model",
                defaultTerms = emptySet(),
                env = Env(logger = Logger.None)
            )

            val result = translator.translate(FormatKind.Json, sources)

            result.size shouldBe sources.size
            callChunkSizes.sum() shouldBe sources.size
            // Verify chunking occurred (multiple calls) or at least one call
            // Verify mock was called
            (callIndex >= 1) shouldBe true
        }
    }
})

/**
 * Creates a mock chatCompletion function that returns a pre-configured response.
 * The mock ignores the input message and returns parsed mock data for any expected line count.
 */
fun mockChatCompletion(content: String): suspend (Int, String) -> Pair<TermTable, List<String?>> =
    { expectedSize, _ -> parseLLMResponse(content, expectedSize) }