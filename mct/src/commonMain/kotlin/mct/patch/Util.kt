package mct.patch

import kotlinx.coroutines.flow.toList
import mct.MCTPattern
import mct.MCTWorkspace
import mct.extractAll
import mct.kit.TranslationMapping
import mct.model.patch.replace

internal suspend fun MCTWorkspace.evaluateReplacementGroups(
    pattern: MCTPattern,
    mapping: TranslationMapping
) = extractAll(pattern).toList().replace(mapping)