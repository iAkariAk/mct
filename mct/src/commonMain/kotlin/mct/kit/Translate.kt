package mct.kit

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import mct.model.patch.ExtractionGroup
import mct.model.patch.contents
import mct.serializer.MCTJson
import mct.serializer.Snbt
import mct.text.TextCompound

typealias TranslationMapping = Map<String, String>
typealias TranslationPool = Set<String>

private fun trySimply(text: String): String = runCatching {
    val tc = MCTJson.decodeFromString<TextCompound>(text)
    MCTJson.encodeToString(tc)
}.getOrElse {
    runCatching {
        val tc = Snbt.decodeFromString<TextCompound>(text)
        Snbt.encodeToString(tc)
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

