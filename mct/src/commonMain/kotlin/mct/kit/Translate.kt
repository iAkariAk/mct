package mct.kit

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonElement
import mct.command.MCCommandJson
import mct.model.patch.ExtractionContent
import mct.model.patch.ExtractionGroup
import mct.model.patch.PointedExtractionContent
import mct.model.patch.contents
import mct.model.text.TextComponent
import mct.model.text.encodeToIR
import mct.serializer.Snbt
import mct.util.decodeFromString
import mct.util.formatir.toIR
import mct.util.formatir.toJsonElement
import mct.util.formatir.toNbtTag
import net.benwoodworth.knbt.NbtTag
import kotlin.jvm.JvmName

typealias TranslationMapping = Map<String, String?>
typealias TranslationPool = Set<String>

private fun trySimply(text: String): String = runCatching {
    val raw = MCCommandJson.decodeFromString<JsonElement>(text).toIR()
    MCCommandJson.encodeToString(JsonElement.serializer(), TextComponent.fromIR(raw).encodeToIR(true).toJsonElement())
}.getOrElse {
    runCatching {
        val raw = Snbt.decodeFromString<NbtTag>(text).toIR()
        Snbt.encodeToString(NbtTag.serializer(), TextComponent.fromIR(raw).encodeToIR(true).toNbtTag())
    }.getOrElse {
        text
    }
}


@JvmName($$"PointedExtractionContent$exportIntoPool")
fun List<PointedExtractionContent>.exportIntoPool(simply: Boolean): TranslationPool = flatMapTo(mutableSetOf()) {
    it.content.contents().map { content ->
        if (!simply) content else trySimply(content)
    }
}

@JvmName($$"ExtractionContent$exportIntoPool")
fun List<ExtractionContent>.exportIntoPool(simply: Boolean): TranslationPool = flatMapTo(mutableSetOf()) {
    it.contents().map { content ->
        if (!simply) content else trySimply(content)
    }
}

@JvmName($$"ExtractionGroup$exportIntoPool")
fun List<ExtractionGroup>.exportIntoPool(simply: Boolean): TranslationPool = flatMapTo(mutableSetOf()) {
    it.extractions.flatMap { extraction ->
        val contents = extraction.contents()
        if (!simply) contents else contents.map(::trySimply)
    }
}

