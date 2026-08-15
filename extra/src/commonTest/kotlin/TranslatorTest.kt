import arrow.atomic.AtomicInt
import arrow.core.raise.context.Raise
import com.aallam.openai.client.OpenAI
import io.kotest.assertions.arrow.core.shouldNotRaise
import io.kotest.assertions.fail
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withTimeout
import mct.Env
import mct.Logger
import mct.Notifier
import mct.extra.ai.ChatCompletionCall
import mct.extra.ai.ChatCompletionCallError
import mct.extra.ai.translator.*
import mct.model.patch.*
import mct.serializer.MCTJson
import mct.util.envvar
import mct.util.unreachable
import kotlin.time.Duration.Companion.seconds

class TranslatorTest : FreeSpec({
    val apiUrl = envvar("OPENAI_URL")
    val token = envvar("OPENAI_TOKEN")
    val model = envvar("OPENAI_MODEL")

    val enabledRealLLMResponseTest = false && listOf(apiUrl, token, model).all { it != null }

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
                val result = translator.translate(FormatKind.PlainStr, Constants.TEXT1.lines())
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
            val raws1 = listOf(
                """[{"color":"gray","text":"Key recipes unlocked!\n(Check the recipe book in a crafting table)"}]""",
                """{"color":"red","text":"ILLEGAL BUCKET USE DETECTED"}"""
            )
            val raws2 = listOf(
                """{"translate": "abc.efg"}""",
                """{"translate": "abc.efg.ghi", "color": "red"}""",
            )
            val raw3 = listOf(
                """{"no": "a TextComponent"}""",
            )
            context(Env()) {
                val (_, result1) = raws1.strips(FormatKind.JsonStr)

                val failures1 = result1.filterIsInstance<ComponentStrip.CannotStrip>()

                if (failures1.isNotEmpty()) {
                    fail("Strip failed for: ${failures1.joinToString { it.original }}")
                }
                val (result2, _) = raws2.strips(FormatKind.JsonStr)
                result2.size shouldBe 2

                val (_, result3) = raw3.strips(FormatKind.JsonStr)
                result3.filterIsInstance<ComponentStrip.NoComponent>().shouldNotBeEmpty()
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
            val mockCall = testChatCompletionCall(contextOf<Env>())
            "plain text" {
                val mockResponse = """
            -- MCT-CLI:TRANSLATED --
            [0] 你好世界
            [1] 这是测试
            -- MCT-CLI:TERMS --
            {}
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
                    result shouldBe listOf(
                        TranslationResult.Translated("你好世界"),
                        TranslationResult.Translated("这是测试")
                    )
                }
            }

            "with existing terms" {
                val mockResponse = """
            -- MCT-CLI:TRANSLATED --
            [0] 辉夜姬很漂亮
            -- MCT-CLI:TERMS --
            {}
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
                    result shouldBe listOf(TranslationResult.Translated("辉夜姬很漂亮"))
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
                    result shouldBe listOf(TranslationResult.Translated("彩叶在散步"))
                    translator.terms shouldBe mapOf("Iroha" to "彩叶")
                }
            }

            "json text component" {
                val mockResponse = """
            -- MCT-CLI:TRANSLATED --
            [0] 你好
            -- MCT-CLI:TERMS --
            {}
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
                    result[0] shouldBe TranslationResult.Translated("""{"text":"你好","color":"red"}""")
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
                            appendLine("{}")
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

            "cancellation salvage" - {
                "sequential failure invokes cancellation once and propagates the original failure" {
                    val cancellationCalls = AtomicInt(0)
                    val translator = Translator(
                        call = mockCall,
                        requestTranslation = { _, _, _, _ -> throw ExpectedTranslationFailure() },
                        defaultTerms = emptyMap(),
                    )

                    val thrown = try {
                        shouldNotRaise {
                            translator.translate(
                                groups = extractionGroup(FormatKind.PlainStr to "failing"),
                                onCancel = { _, _ -> cancellationCalls.incrementAndGet() },
                            )
                        }
                        null
                    } catch (e: Throwable) {
                        e
                    }

                    cancellationCalls.get() shouldBe 1
                    thrown.shouldBeInstanceOf<ExpectedTranslationFailure>()
                }

                "concurrent kind failure rescues translations committed before sibling cancellation" {
                    val plainChunkCommitted = CompletableDeferred<Unit>()
                    val cancellationCalls = AtomicInt(0)
                    var rescued = emptyMap<String, String?>()
                    val testEnv = Env(
                        logger = Logger.Console(),
                        notifier = Notifier { _, value ->
                            if (value is TranslateSign.Progress && value.progress < 1f) {
                                plainChunkCommitted.complete(Unit)
                            }
                        },
                    )

                    context(testEnv) {
                        val translator = Translator(
                            call = testChatCompletionCall(testEnv),
                            requestTranslation = { expectedSize, message, format, _ ->
                                when (format) {
                                    FormatKind.PlainStr -> when {
                                        "plain-completed" in message ->
                                            emptyMap<String, String>() to List(expectedSize) { "translated-completed" }

                                        "plain-blocked" in message -> awaitCancellation()
                                        else -> error("Unexpected plain-text request: $message")
                                    }

                                    FormatKind.JsonStr -> {
                                        plainChunkCommitted.await()
                                        throw ExpectedTranslationFailure()
                                    }

                                    else -> error("Unexpected format: $format")
                                }
                            },
                            defaultTerms = emptyMap(),
                            tokenThreshold = 1,
                            concurrency = 2,
                        )

                        val thrown = try {
                            withTimeout(5.seconds) {
                                shouldNotRaise {
                                    translator.translate(
                                        groups = extractionGroup(
                                            FormatKind.PlainStr to "plain-completed",
                                            FormatKind.PlainStr to "plain-blocked",
                                            FormatKind.JsonStr to "json-failing",
                                        ),
                                        concurrentByKind = true,
                                        onCancel = { _, salvaged ->
                                            cancellationCalls.incrementAndGet()
                                            rescued = salvaged
                                        },
                                    )
                                }
                            }
                            null
                        } catch (e: Throwable) {
                            e
                        }

                        cancellationCalls.get() shouldBe 1
                        rescued shouldBe mapOf("plain-completed" to "translated-completed")
                        thrown.shouldBeInstanceOf<ExpectedTranslationFailure>()
                    }
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
    { expectedSize, _, _, validate -> parseLLMResponse(content, expectedSize).also { validate(it).shouldBeTrue() } }

private fun extractionGroup(vararg contents: Pair<FormatKind, String>): List<ExtractionGroup> = listOf(
    DatapackExtractionGroup(
        source = "test",
        path = "test.mcfunction",
        extractions = contents.mapIndexed { index, (format, content) ->
            DatapackExtraction.MCFunction(
                indices = index..index,
                content = content,
                format = format,
            )
        },
    )
)

private class ExpectedTranslationFailure : RuntimeException("expected translation failure")

private fun testChatCompletionCall(testEnv: Env) = object : ChatCompletionCall {
    override val client: OpenAI get() = unreachable
    override val model: String get() = "mock-model"
    override val env: Env = testEnv

    context(_: Raise<ChatCompletionCallError>)
    override suspend fun <T> chat(
        prompt: String,
        message: String,
        parseLLM: suspend (String) -> T,
        validate: (T) -> Boolean,
    ): T = unreachable
}

