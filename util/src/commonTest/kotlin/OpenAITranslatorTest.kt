import io.kotest.assertions.arrow.core.shouldNotRaise
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import mct.util.system.envvar
import mct.util.translator.OpenAITranslator
import mct.util.translator.Term
import mct.util.translator.TermType
import mct.util.translator.parseLLMResponse

class OpenAITranslatorTest : StringSpec({
    val apiUrl = envvar("OPENAI_URL")
    val token = envvar("OPENAI_TOKEN")
    val model = envvar("OPENAI_MODEL")
    fun translator() = shouldNotRaise { OpenAITranslator(apiUrl!!, token!!, model!!, defaultTerms = emptySet()) }


    "parse test" {
        val response = """
            -- MCT-CLI:TRANSLATED --
            a
            b
            c
            -- MCT-CLI:TERMS --
            [{"source":"Iroha","target":"彩叶","type":"name"}]
            -- MCT-CLI:END --
        """.trimIndent()
        val (terms, translated) = parseLLMResponse(response)
        terms shouldBe setOf(Term("Iroha", "彩叶", TermType.Name))
        translated shouldBe listOf("a", "b", "c")
    }

    val testEnabled = listOf(apiUrl, token, model).all { it != null }

    if (!testEnabled) {
        println("WARNING: Test was disabled due to no configure for OpenAI in env vars, please add `OPENAI_URL`, `OPENAI_TOKEN` and `OPENAI_MODEL`.")
    }

    "translate test".config(enabled = testEnabled) {
        val translator = translator()
        val result = translator.translate(TEST_TEXT.lines())
        println("translated: $result")
        println("terms: ${translator.terms}")
    }
})
