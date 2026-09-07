package mct.patch

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import mct.MCTPattern
import mct.MCTWorkspace
import mct.extractAll
import mct.kit.TranslationMapping
import mct.model.patch.*
import mct.util.IO

@Suppress("UNCHECKED_CAST") // safe
internal suspend fun MCTWorkspace.evaluateReplacementGroups(
    pattern: MCTPattern,
    mapping: TranslationMapping,
    extractionGroups: suspend () -> Triple<Flow<RegionExtractionGroup>, Flow<DatapackExtractionGroup>, Flow<CextExtractionGroup>> = {
        extractAll(pattern)
    }
): Patch.Immediate.ReplacementGroups = coroutineScope {
    val (regionEG, datapackEG, cextEG) = extractionGroups()
    val region = async(Dispatchers.IO) { regionEG.toList().replace(mapping) as List<RegionReplacementGroup> }
    val datapack = async(Dispatchers.IO) { datapackEG.toList().replace(mapping) as List<DatapackReplacementGroup> }
    val cext = async(Dispatchers.IO) { cextEG.toList().replace(mapping) as List<CextReplacementGroup> }
    Patch.Immediate.ReplacementGroups(region.await(), datapack.await(), cext.await())
}