import com.aallam.openai.api.chat.ChatChoice
import com.aallam.openai.api.chat.ChatCompletion
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import io.kotest.assertions.arrow.core.shouldNotRaise
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import mct.Env
import mct.FormatKind
import mct.Logger
import mct.extra.translator.OpenAITranslator
import mct.extra.translator.Term
import mct.extra.translator.TermType
import mct.extra.translator.parseLLMResponse
import mct.util.envvar

class OpenAITranslatorTest : FreeSpec({
    val apiUrl = envvar("OPENAI_URL")
    val token = envvar("OPENAI_TOKEN")
    val model = envvar("OPENAI_MODEL")
    fun translator() = shouldNotRaise { OpenAITranslator(apiUrl!!, token!!, model!!, defaultTerms = emptySet()) }

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

            val mockChat: suspend (String) -> ChatCompletion = { message ->
                val idx = callIndex++
                // Count [N] markers in the input message body
                val body = message.substringAfter("-- MCT-CLI:START --").trim()
                val chunkSize = body.lines().count { it.startsWith("[") }
                callChunkSizes += chunkSize

                val translated = (0 until chunkSize).joinToString("\n") { i ->
                    "[$i] chunk${idx}_line${i}"
                }
                val content = buildString {
                    appendLine("-- MCT-CLI:TRANSLATED --")
                    appendLine(translated)
                    appendLine("-- MCT-CLI:TERMS --")
                    appendLine("[]")
                    append("-- MCT-CLI:END --")
                }
                ChatCompletion(
                    id = "mock-$idx",
                    created = 0,
                    model = ModelId("mock-model"),
                    choices = listOf(
                        ChatChoice(
                            index = 0,
                            message = ChatMessage(
                                role = ChatRole.Assistant,
                                content = content
                            )
                        )
                    )
                )
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
 */
fun mockChatCompletion(content: String): suspend (String) -> ChatCompletion = {
    ChatCompletion(
        id = "mock-id",
        created = 0,
        model = ModelId("mock-model"),
        choices = listOf(
            ChatChoice(
                index = 0,
                message = ChatMessage(
                    role = ChatRole.Assistant,
                    content = content
                )
            )
        )
    )
}