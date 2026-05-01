package mct.region

import io.kotest.assertions.arrow.core.shouldNotRaise
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import mct.pointer.DataPointer
import mct.pointer.decodeFromString

class NbtExtractPatternTest : FreeSpec({
    "match test" - {
        listOf(
            ">#>#Entities>0>#FireworksItem>#tag>#display>#Name",
            ">#>#Entities>0>#CustomName",
            ">#>#block_entities>5>#front_text>#messages"
        ).forEach { ptr ->
            val ptr = shouldNotRaise {
                DataPointer.decodeFromString(ptr)
            }
            "BUILTIN_SET should match $ptr" {
                BuiltinRegionPatterns.any { it.match(ptr) } shouldBe true
            }
        }
    }
})
