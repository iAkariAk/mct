package mct.nbt

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import mct.Logger
import mct.model.patch.ExtractionContent
import mct.pointer.encodeToString

class NbtCommandPatternTest : FreeSpec({
    "nested `Command` should be extracted" {
        context(Logger.Console()) {
            val extractions = extractTextFromSnbt("""{"":{tag:{Command:"say Ciallo"}}}""").toList()
            extractions.size shouldBe 1
            val extraction = extractions.single()
            extraction.pointer.encodeToString() shouldBe ">#>#tag>#Command"
            extraction.format shouldBe PlainStr
            extraction.content.shouldBeInstanceOf<ExtractionContent.Command>()
            extraction.content.raw shouldBe "say Ciallo"
            extraction.content.locations.size shouldBe 1
            val location = extraction.content.locations.single()
            location.content shouldBe "Ciallo"
        }
    }
})