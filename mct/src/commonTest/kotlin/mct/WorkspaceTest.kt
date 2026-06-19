package mct

import io.kotest.assertions.arrow.core.shouldNotRaise
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import kotlinx.coroutines.flow.toList
import mct.dp.backfillDatapack
import mct.dp.extractFromDatapack
import mct.model.patch.DatapackReplacementGroup
import mct.model.patch.RegionReplacementGroup
import mct.model.patch.replaceSimply
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
            extractions.shouldBeEmpty() // due to the mapping not existing any datapack
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