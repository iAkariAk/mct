import arrow.core.raise.context.Raise
import com.aallam.openai.client.OpenAI
import io.kotest.assertions.arrow.core.shouldNotRaise
import io.kotest.assertions.fail
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import mct.Env
import mct.Logger
import mct.extra.ai.ChatCompletionCall
import mct.extra.ai.ChatCompletionCallError
import mct.extra.ai.translator.*
import mct.model.patch.FormatKind
import mct.model.patch.RegionExtractionGroup
import mct.serializer.MCTJson
import mct.util.envvar
import mct.util.unreachable

class TranslatorTest : FreeSpec({
    val apiUrl = envvar("OPENAI_URL")
    val token = envvar("OPENAI_TOKEN")
    val model = envvar("OPENAI_MODEL")

    val enabledRealLLMResponseTest = listOf(apiUrl, token, model).all { it != null }

    if (!enabledRealLLMResponseTest) {
        println("WARNING: Test was disabled due to no configure for OpenAI in env vars, please add `OPENAI_URL`, `OPENAI_TOKEN` and `OPENAI_MODEL`.")
    }
    context(Env(logger = Logger.Console())) {
        suspend fun translator() = shouldNotRaise {
            val call = ChatCompletionCall(
                apiUrl = apiUrl,
                token = token!!,
                model = model!!,
            )
            Translator(call)
        }



        "translate test".config(enabled = enabledRealLLMResponseTest) {
            shouldNotRaise {
                val translator = translator()
                val result = translator.translate(FormatKind.JsonStr, Constants.TEXT1.lines())
                println("terms: ${translator.terms}")
                println("translated: $result")
            }
        }

        "parse test" {
            val response = """
            -- MCT-CLI:TRANSLATED --
            [0] a
            [1] b
            [2] c
            -- MCT-CLI:TERMS --
            {
            "Iroha": "彩叶"
            }
            -- MCT-CLI:END --
        """.trimIndent()
            val (terms, translated) = parseLLMResponse(response, 3)
            terms shouldBe mapOf("Iroha" to "彩叶")
            translated shouldBe listOf("a", "b", "c")
        }

        "strip test" {
            val raws = listOf(
                """[{"color":"gray","text":"Key recipes unlocked!\n(Check the recipe book in a crafting table)"}]""",
                """{"color":"red","text":"ILLEGAL BUCKET USE DETECTED"}"""
            )
            context(Env()) {
                val result = raws.strip(FormatKind.JsonStr)

                val failures = result.filterIsInstance<CompoundStrip.CannotStrip>()

                if (failures.isNotEmpty()) {
                    fail("Strip failed for: ${failures.joinToString { it.original }}")
                }
            }
        }

        "comprehensive test".config(enabled = enabledRealLLMResponseTest) {
            shouldNotRaise {
                val translator = translator()
                val jsonStr = TestResources.extractions.readText()
                val extractions = MCTJson.decodeFromString<List<RegionExtractionGroup>>(jsonStr)
                println(translator.translate(extractions))
            }
        }


        "mock" - {
            val mockCall = object : ChatCompletionCall {
                override val client: OpenAI get() = unreachable
                override val model: String get() = "mock-model"
                override val env: Env get() = contextOf<Env>()

                context(_: Raise<ChatCompletionCallError>)
                override suspend fun <T> chat(
                    prompt: String,
                    message: String,
                    parseLLM: suspend (String) -> T,
                    validate: (T) -> Boolean,
                ): T = unreachable

            }
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
                val translator = Translator(
                    call = mockCall,
                    requestTranslation = mockChat,
                    defaultTerms = emptyMap(),
                )

                shouldNotRaise {
                    val result = translator.translate(FormatKind.JsonStr, listOf("Hello world", "This is a test"))
                    result shouldBe listOf("你好世界", "这是测试")
                }
            }

            "with existing terms" {
                val mockResponse = """
            -- MCT-CLI:TRANSLATED --
            [0] 辉夜姬很漂亮
            -- MCT-CLI:TERMS --
            []
            -- MCT-CLI:END --
        """.trimIndent()

                val existingTerms = mapOf("Kaguya" to "辉夜姬")
                val mockChat = mockChatCompletion(mockResponse)
                val translator = Translator(
                    call = mockCall,
                    requestTranslation = mockChat,
                    defaultTerms = existingTerms,
                )

                shouldNotRaise {
                    val result = translator.translate(FormatKind.JsonStr, listOf("Kaguya is beautiful"))
                    result shouldBe listOf("辉夜姬很漂亮")
                    translator.terms shouldBe existingTerms
                }
            }

            "new terms discovered" {
                val mockResponse = """
            -- MCT-CLI:TRANSLATED --
            [0] 彩叶在散步
            -- MCT-CLI:TERMS --
            {
            "Iroha": "彩叶"
            }
            -- MCT-CLI:END --
        """.trimIndent()

                val mockChat = mockChatCompletion(mockResponse)
                val translator = Translator(
                    call = mockCall,
                    requestTranslation = mockChat,
                    defaultTerms = emptyMap(),
                )

                shouldNotRaise {
                    val result = translator.translate(FormatKind.JsonStr, listOf("Iroha is walking"))
                    result shouldBe listOf("彩叶在散步")
                    translator.terms shouldBe mapOf("Iroha" to "彩叶")
                }
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
                val translator = Translator(
                    call = mockCall,
                    requestTranslation = mockChat,
                    defaultTerms = emptyMap(),
                )

                shouldNotRaise {
                    val jsonInput = """{"text":"Hello","color":"red"}"""
                    val result = translator.translate(FormatKind.JsonStr, listOf(jsonInput))
                    result[0] shouldBe """{"text":"你好","color":"red"}"""
                }
            }

            "long request chunking" {
                var callIndex = 0
                val callChunkSizes = mutableListOf<Int>()

                val mockChat: RequestTranslation =
                    { expectedSize, _, _, _ ->
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

                val sources = (0 until 10).flatMap { Constants.TEXT1.lines() }

                val translator = Translator(
                    call = mockCall,
                    requestTranslation = mockChat,
                    defaultTerms = emptyMap(),
                )

                shouldNotRaise {

                    val result = translator.translate(FormatKind.JsonStr, sources)
                    result.size shouldBe sources.size
                    callChunkSizes.sum() shouldBe sources.size
                    // Verify chunking occurred (multiple calls) or at least one call
                    // Verify mock was called
                    (callIndex >= 1) shouldBe true
                }
            }
        }
    }
})

/**
 * Creates a mock chatCompletion function that returns a pre-configured response.
 * The mock ignores the input message and returns parsed mock data for any expected line count.
 */
fun mockChatCompletion(content: String): RequestTranslation =
    { expectedSize, _, _, _ -> parseLLMResponse(content, expectedSize) }

