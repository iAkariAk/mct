package mct.patch

import arrow.fx.coroutines.parMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import mct.MCTWorkspace
import mct.model.patch.PatchPreprocessing
import mct.model.patch.PatchPreprocessingOperation
import mct.util.io.readBytes
import mct.util.io.readText
import mct.util.io.writeBytes
import mct.util.io.writeText
import mct.util.patch
import okio.Path

sealed interface PreprocessingOperationResult {
    val path: Path

    data class Success(override val path: Path) : PreprocessingOperationResult
    data class Failure(override val path: Path, val reason: Throwable) : PreprocessingOperationResult
}

fun MCTWorkspace.applyPreprocessing(preprocessing: PatchPreprocessing): Pair<Flow<PreprocessingOperationResult>, Flow<PreprocessingOperationResult>> {
    val ordered = preprocessing.ordered.asFlow().map { applyPreprocessingOperation(it) }
    val unordered = preprocessing.unordered.asFlow().parMap { applyPreprocessingOperation(it) }
    return ordered to unordered
}

private fun MCTWorkspace.applyPreprocessingOperation(operation: PatchPreprocessingOperation): PreprocessingOperationResult {
    val path = operation.path
    runCatching {
        when (operation) {
            is AddFile -> {
                logger.info { "Preprocessing: Adding file $path" }
                path.writeBytes(operation.content)
            }

            is RemoveFile -> {
                logger.info { "Preprocessing: Removing file $path" }
                fs.deleteRecursively(path)
            }

            is PatchTextFile -> {
                logger.info { "Preprocessing: Patching text file $path" }
                val original = path.readText()
                val patched = original.patch(operation.patches)
                path.writeText(patched)
            }

            is PatchBinaryFile -> {
                logger.info { "Preprocessing: Patching binary file $path" }
                val original = path.readBytes()
                val patched = original.patch(operation.patches)
                path.writeBytes(patched)
            }
        }
    }.onFailure {
        logger.error { "Preprocessing: Fail to handle $path" }
        return PreprocessingOperationResult.Failure(path, it)
    }
    logger.info { "Preprocessing: Handled $path" }
    return PreprocessingOperationResult.Success(path)
}


