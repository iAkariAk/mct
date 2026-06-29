package mct.mtl

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class MTLXTest : FreeSpec({
    "parse MLTX" {
        val mtlx = MTLX.fromString(
            """
                    ---mtl---
                    |Shimamura Co.| ==> |Shimamura|
                    ---raw---
                    |Adachi| ==> |Shimamura|
                """.trimIndent()
        )
        mtlx.mtlMappings.size shouldBe 1
        val (_, left, right) = mtlx.mtlMappings.first()
        left.shouldBeInstanceOf<MTLLiteral>()
        right.shouldBeInstanceOf<MTLLiteral>()
        left.content shouldBe "Shimamura Co."
        right.content shouldBe "Shimamura"
        mtlx.rawMappings shouldBe mapOf("Adachi" to "Shimamura")
    }
})