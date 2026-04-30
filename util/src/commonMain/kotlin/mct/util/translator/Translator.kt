package mct.util.translator

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mct.Env
import mct.ExtractionGroup
import mct.ReplacementGroup
import mct.kit.replace

data class TranslateResponse(val texts: List<String>, val terms: TermTable)

typealias TermTable = Set<Term>

@Serializable
data class Term(val source: String, val target: String, val type: TermType)

@Serializable
enum class TermType {
    @SerialName("name")
    Name,

    @SerialName("term")
    Term
}

interface Translator {
    val env: Env
    val terms: TermTable

    suspend fun translate(sources: List<String>): List<String>
}

suspend fun Translator.translate(extractionGroups: List<ExtractionGroup>): List<ReplacementGroup> {
    val allTexts = extractionGroups
        .flatMap { it.extractions }
        .map { it.content }
        .filter { it.isNotBlank() }
        .distinct()

    val batchSize = 8
    val batches = allTexts.chunked(batchSize)
    val mapping = mutableMapOf<String, String>()

    val failedCount = mutableListOf<Int>()
    batches.forEachIndexed { index, batch ->
        val pct = (index + 1).toFloat() / batches.size
        env.logger.sign<TranslateSign> { TranslateSign.Progress(pct) }
        env.logger.info { "[${index + 1}/${batches.size}] 正在翻译 ${batch.size} 条..." }
        try {
            val translated = translate(batch)
            if (translated.size != batch.size) {
                env.logger.warning { "翻译返回 ${translated.size} 条, 期望 ${batch.size} 条, 跳过" }
                failedCount.add(index)
            } else {
                batch.zip(translated).forEach { (src, tgt) -> mapping[src] = tgt }
                env.logger.info { "[${index + 1}/${batches.size}] 完成" }
            }
        } catch (e: Exception) {
            env.logger.error { "错误: ${e.message}, 跳过此批次" }
            failedCount.add(index)
        }
    }
    env.logger.sign<TranslateSign> { TranslateSign.Progress(1f) }

    if (failedCount.isNotEmpty()) {
        env.logger.info { "共 ${failedCount.size} 个批次失败 (${failedCount.map { it + 1 }}), ${mapping.size} 条已翻译" }
    }

    return extractionGroups.replace(mapping)
}

