package mct

import io.kotest.assertions.arrow.core.shouldNotRaise
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import kotlinx.coroutines.flow.toList
import mct.DatapackReplacementGroup
import mct.RegionReplacementGroup
import mct.dp.backfillDatapack
import mct.dp.extractFromDatapack
import mct.kit.replaceSimply
import mct.region.backfillRegion
import mct.region.extractFromRegion

class WorkspaceTest : StringSpec({
    "open map" {
        val workspace = TestMapWorkspace()
        shouldNotThrowAny {
            println(workspace.level)
        }
    }

    "extract" {
        val workspace = TestMapWorkspace()

        shouldNotRaise {
            println(workspace.extractFromRegion().toList())
        }
    }

    "extract from datapack" {
        val workspace = TestMapWorkspace()
        shouldNotRaise {
            val extractions = workspace.extractFromDatapack().toList()
            extractions.shouldNotBeEmpty()
        }
    }

    "backfill" {
        val workspace = TestMapWorkspace()
        shouldNotRaise {
            val extractions = workspace.extractFromRegion().toList()
            val replacements = extractions.replaceSimply {
                "Kaguya&Iroha"
            }
            workspace.backfillRegion(replacements.filterIsInstance<RegionReplacementGroup>())

            // Validate if it corrupts

            val new = workspace.extractFromRegion().toList()
            println(new)
        }
    }

    "backfill datapack" {
        val workspace = TestMapWorkspace()
        shouldNotRaise {
            val extractions = workspace.extractFromDatapack().toList()
            val replacements = extractions.replaceSimply {
                "Kaguya&Iroha"
            }
            workspace.backfillDatapack(replacements.filterIsInstance<DatapackReplacementGroup>())

            // Validate if it corrupts

            val new = workspace.extractFromDatapack().toList()
            println(new)
        }
    }
})