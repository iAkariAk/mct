package mct.kit

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonElement
import mct.model.patch.ExtractionGroup
import mct.model.patch.contents
import mct.model.text.TextCompound
import mct.model.text.encodeToIR
import mct.serializer.Snbt
import mct.util.MCJson
import mct.util.decodeFromMCJson
import mct.util.formatir.toIR
import mct.util.formatir.toJsonElement
import mct.util.formatir.toNbtTag
import net.benwoodworth.knbt.NbtTag

typealias TranslationMapping = Map<String, String?>
typealias TranslationPool = Set<String>

private fun trySimply(text: String): String = runCatching {
    val raw = decodeFromMCJson<JsonElement>(text).toIR()
    MCJson.encodeToString(JsonElement.serializer(), TextCompound.fromIR(raw).encodeToIR(true).toJsonElement())
}.getOrElse {
    runCatching {
        val raw = Snbt.decodeFromString<NbtTag>(text).toIR()
        Snbt.encodeToString(NbtTag.serializer(), TextCompound.fromIR(raw).encodeToIR(true).toNbtTag())
    }.getOrElse {
        text
    }
}


fun List<ExtractionGroup>.exportIntoPool(simply: Boolean): TranslationPool = flatMapTo(mutableSetOf()) {
    it.extractions.flatMap { extraction ->
        val contents = extraction.contents()
        if (!simply) contents else contents.map(::trySimply)
    }
}

