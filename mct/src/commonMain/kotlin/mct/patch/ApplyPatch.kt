package mct.patch

import arrow.core.raise.Raise
import arrow.core.raise.context.bind
import arrow.core.raise.either
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.associate
import kotlinx.coroutines.launch
import mct.MCTWorkspace
import mct.cext.backfillCext
import mct.dp.backfillDatapack
import mct.model.patch.*
import mct.region.backfillRegion
import mct.util.IO
import mct.util.NotMatchedItem
import mct.util.io.HashKind.SHA1
import mct.util.io.computeHashTree
import mct.util.tripartition

sealed interface PatchResult {
    data class Success(val warning: Map<String, NotMatchedItem<String>>) : PatchResult
    data class ValidationFailure(val unmatched: Map<String, NotMatchedItem<String>>) : PatchResult
}

context(_: Raise<PatchError>)
suspend fun MCTWorkspace.applyPatch(
    patch: Patch, strategy: PatchValidationFailureStrategy = Warning
): PatchResult {
    val validation = patch.validation
    val needValidating = validation != null && strategy != Ignore
    val notMatchedItems = if (needValidating) {
        val actual =
            fs.computeHashTree(rootDir, SHA1).associate { (path, hash) -> path.relativeTo(rootDir).toString() to hash }
        val expected = validation.hashTree
        actual.filter { (path, hash) -> hash != expected[path] }
            .mapValues { (path, hash) -> NotMatchedItem(expected[path]!!, hash) }
    } else null

    if (validation != null && strategy == Failure && notMatchedItems?.isNotEmpty() == true) {
        return PatchResult.ValidationFailure(notMatchedItems)
    }

    val replacementGroups = when (patch) {
        is Patch.Deferred -> evaluateReplacementGroups(patch.pattern, patch.mapping)
        is Patch.Immediate -> patch.replacementGroups
    }

    coroutineScope {
        val (region, datapack, cext) = replacementGroups.tripartition<ReplacementGroup, RegionReplacementGroup, DatapackReplacementGroup, CextReplacementGroup>()
        launch(Dispatchers.IO) {
            either {
                backfillRegion(region)
            }.mapLeft(::PatchError).bind()
        }
        launch {
            backfillDatapack(datapack)
        }
        launch(Dispatchers.IO) {
            backfillCext(cext)
        }
    }

    return PatchResult.Success(notMatchedItems ?: emptyMap())
}