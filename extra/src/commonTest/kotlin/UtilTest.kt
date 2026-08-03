package mct.extra.ai

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class UtilTest : FreeSpec({
    "escape especial unicode" {
        escapeEspecialUnicode("\uD800\uDC00") shouldBe "\uD800\uDC00"
        escapeEspecialUnicode("\uE000") shouldBe $$"$MCT_UNICODE_E000$"
    }

    "unescape especial unicode" {
        unescapeEspecialUnicode(
            $$"""
            $MCT_UNICODE_E000$Money=$(money)
            $MCT_UNICODE_E001$HP=$(hp)
            $MCT_UNICODE_100000$MP=$(mp)
        """.trimIndent()
        ) shouldBe $$"""
            $${'\uE000'}Money=$(money)
            $${'\uE001'}HP=$(hp)
            $${"\uDBC0\uDC00"}MP=$(mp)
        """.trimIndent()
    }
})
