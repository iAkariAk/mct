package mct.kit

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonElement
import mct.model.patch.ExtractionGroup
import mct.model.patch.contents
import mct.model.text.encodeToIR
import mct.serializer.MCTJson
import mct.serializer.Snbt
import mct.model.text.TextCompound
import mct.util.formatir.toIR
import mct.util.formatir.toJsonElement
import mct.util.formatir.toNbtTag
import net.benwoodworth.knbt.NbtTag

typealias TranslationMapping = Map<String, String?>
typealias TranslationPool = Set<String>

private fun trySimply(text: String): String = runCatching {
    val raw = MCTJson.decodeFromString<JsonElement>(text).toIR()
    MCTJson.encodeToString(JsonElement.serializer(), TextCompound.fromIR(raw).encodeToIR(true).toJsonElement())
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

