package mct.patch

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.associate
import mct.MCTPattern
import mct.MCTWorkspace
import mct.kit.TranslationMapping
import mct.model.patch.Patch
import mct.model.patch.PatchMetadata
import mct.model.patch.PatchValidation
import mct.model.patch.PathKind
import mct.util.io.computeHashTree

suspend fun MCTWorkspace.createPatch(
    pattern: MCTPattern,
    mapping: TranslationMapping,
    kind: PathKind,
    validation: Boolean = true,
): Patch = coroutineScope {
    val metadata = level?.let {
        val level = it.data
        PatchMetadata(level.levelName)
    }

    val validation = if (!validation) null else {
        val hashTree =
            fs.computeHashTree(rootDir, SHA1).associate { (path, hash) -> path.relativeTo(rootDir).toString() to hash }
        PatchValidation(hashTree)
    }

    when (kind) {
        Deferred -> Patch.Deferred(metadata, validation, pattern, mapping)
        Immediate -> {
            val replacementGroups = evaluateReplacementGroups(pattern, mapping)
            Patch.Immediate(metadata, validation, replacementGroups)
        }
    }
}
