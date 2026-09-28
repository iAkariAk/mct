package mct.patch

import arrow.core.padZip
import arrow.core.raise.Raise
import arrow.core.raise.context.bind
import arrow.core.raise.either
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.associate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import mct.MCTWorkspace
import mct.cext.backfillCext
import mct.dp.backfillDatapack
import mct.model.patch.Patch
import mct.model.patch.PatchValidationFailureStrategy
import mct.region.backfillRegion
import mct.util.IO
import mct.util.io.HashKind.SHA1
import mct.util.io.computeHashTree
import mct.util.io.unixString

sealed interface PatchResult {
    data class Success(val warnings: List<HashValidatingFailure>) : PatchResult
    data class ValidationFailure(val errors: List<HashValidatingFailure>) : PatchResult
    data class PreprocessFailure(val errors: List<PreprocessingOperationResult.Failure>) : PatchResult
}

sealed interface HashValidatingFailure {
    val key: String

    data class Unmatched(override val key: String, val expected: String, val actual: String) : HashValidatingFailure
    data class Missing(override val key: String) : HashValidatingFailure
    data class Redundant(override val key: String) : HashValidatingFailure
}

private fun Map<String, String>.validate(expected: Map<String, String>): List<HashValidatingFailure> =
    padZip(expected).mapNotNull { (key, value) ->
        val (actual, expected) = value
        when {
            actual == null && expected != null -> HashValidatingFailure.Missing(key)
            actual != null && expected == null -> HashValidatingFailure.Redundant(key)
            actual != expected -> HashValidatingFailure.Unmatched(key, expected!!, actual!!) // safely assert
            else -> null
        }
    }


context(_: Raise<PatchError>)
suspend fun MCTWorkspace.applyPatch(
    patch: Patch, strategy: PatchValidationFailureStrategy = Warning
): PatchResult {
    val validation = patch.validation
    val needValidating = validation != null && strategy != Ignore
    val validatingFailures = if (needValidating) {
        val actual = fs.computeHashTree(rootDir, SHA1).associate { (path, hash) ->
            path.relativeTo(rootDir).unixString() to hash
        }
        actual.validate(validation.hashTree)
    } else null

    if (validation != null && strategy == Failure && validatingFailures?.isNotEmpty() == true) {
        return PatchResult.ValidationFailure(validatingFailures)
    }

    val (ordered, unordered) = applyPreprocessing(patch.preprocessing)
    val operationResults = merge(ordered, unordered).flowOn(Dispatchers.IO).toList()
    val operationErrors = operationResults.filterIsInstance<PreprocessingOperationResult.Failure>()
    if (operationErrors.isNotEmpty()) {
        return PatchResult.PreprocessFailure(operationErrors)
    }

    val replacementGroups = when (patch) {
        is Patch.Deferred -> evaluateReplacementGroups(patch.pattern, patch.mapping)
        is Patch.Immediate -> patch.replacementGroups
    }

    coroutineScope {
        val (region, datapack, cext) = replacementGroups
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

    return PatchResult.Success(validatingFailures ?: emptyList())
}