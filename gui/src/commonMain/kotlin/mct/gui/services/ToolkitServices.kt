package mct.gui.services

import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.schema.generator.json.serialization.SerializationClassJsonSchemaGenerator
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mct.Env
import mct.cli.util.downloadAndSha1
import mct.command.CommandExtractPattern
import mct.command.CommandRegexPattern
import mct.command.extractTextFromCommands
import mct.gui.model.GuiSettings
import mct.gui.model.MCTPatternState
import mct.gui.model.MtlxSource
import mct.gui.platform.copyZipEntry
import mct.gui.platform.createDownloadClient
import mct.gui.platform.ioDispatcher
import mct.gui.util.writeAtomically
import mct.kit.TranslationMapping
import mct.kit.TranslationPool
import mct.kit.exportIntoPool
import mct.model.patch.*
import mct.mtl.MTLX
import mct.mtl.generateMTLXTemplate
import mct.mtl.translateByMTLX
import mct.pointer.DataPointerPattern
import mct.serializer.MCTJson
import mct.util.io.writeJson
import mct.util.io.writeText
import okio.Path.Companion.toPath

/**
 * Write a tool's output through [writeAtomically], which keeps the previous output intact when the
 * write fails. The parent directory is created there as well, matching the panels' promise that a
 * `mustExist = false` destination need not exist yet.
 */
context(env: Env)
private inline fun writeOutputAtomically(output: String, crossinline write: (okio.Path) -> Unit) =
    writeAtomically(env.fs, output.toPath()) { temp -> write(temp) }

/**
 * Stateless implementations for the file-to-file utilities shared with the CLI.
 *
 * Keeping these outside composables makes the toolbox usable from a future batch
 * workflow without duplicating file-format handling.
 */
context(env: Env)
suspend fun flattenTextPool(
    input: String,
    output: String,
    kind: String,
    simply: Boolean,
) = withContext(ioDispatcher) {
    val content = env.fs.read(input.toPath()) { readUtf8() }
    val groups = when (kind) {
        "region" -> MCTJson.decodeFromString<List<RegionExtractionGroup>>(content)
        "datapack" -> MCTJson.decodeFromString<List<DatapackExtractionGroup>>(content)
        "cext" -> MCTJson.decodeFromString<List<CextExtractionGroup>>(content)
        else -> error("未知提取类型: $kind")
    }
    val pool = groups.exportIntoPool(simply)
    writeOutputAtomically(output) { it.writeJson(pool, pretty = GuiSettings.prettyOutput) }
    env.logger.info { "已将 ${groups.size} 个分组导出为 ${pool.size} 条文本: $output" }
}

context(env: Env)
suspend fun unflattenTextPool(
    input: String,
    mapping: String,
    output: String,
) = withContext(ioDispatcher) {
    val groups = env.fs.read(input.toPath()) { readUtf8() }
        .let { MCTJson.decodeFromString<List<ExtractionGroup>>(it) }
    val translations = env.fs.read(mapping.toPath()) { readUtf8() }
        .let { MCTJson.decodeFromString<TranslationMapping>(it) }
    val replacements = groups.replace(translations)
    writeOutputAtomically(output) { it.writeJson(replacements, pretty = GuiSettings.prettyOutput) }
    env.logger.info { "已将 ${translations.size} 条映射应用到 ${replacements.size} 个替换分组: $output" }
}

context(env: Env)
suspend fun generateMtlxTemplate(
    input: String,
    output: String,
    source: MtlxSource,
) = withContext(ioDispatcher) {
    val raw = env.fs.read(input.toPath()) { readUtf8() }
    val (template, count) = when (source) {
        MtlxSource.Pool -> {
            val pool = MCTJson.decodeFromString<TranslationPool>(raw)
            pool.generateMTLXTemplate() to pool.size
        }

        MtlxSource.Mapping -> {
            val mapping = MCTJson.decodeFromString<TranslationMapping>(raw)
            mapping.generateMTLXTemplate() to mapping.size
        }
    }
    writeOutputAtomically(output) { it.writeText(template.render()) }
    env.logger.info { "已从 $count 条${source.label}生成 MTLX 模板: $output" }
}

context(env: Env)
suspend fun translateByMtlx(
    mtlxPath: String,
    poolPath: String,
    output: String,
) = withContext(ioDispatcher) {
    val mtlx = env.fs.read(mtlxPath.toPath()) { readUtf8() }.let(MTLX::fromString)
    val pool = env.fs.read(poolPath.toPath()) { readUtf8() }
        .let { MCTJson.decodeFromString<TranslationPool>(it) }
    val mapping = pool.translateByMTLX(mtlx)
    writeOutputAtomically(output) { it.writeJson(mapping, pretty = GuiSettings.prettyOutput) }
    val translated = mapping.count { it.value != null }
    env.logger.info { "MTLX 已匹配 $translated/${pool.size} 条文本: $output" }
}

context(env: Env)
suspend fun replaceAllExtractions(
    input: String,
    output: String,
    replacement: String,
) = withContext(ioDispatcher) {
    val groups = env.fs.read(input.toPath()) { readUtf8() }
        .let { MCTJson.decodeFromString<List<ExtractionGroup>>(it) }
    val replacements = groups.replaceSimply { replacement }
    writeOutputAtomically(output) { it.writeJson(replacements, pretty = GuiSettings.prettyOutput) }
    env.logger.info { "已为 ${groups.size} 个分组生成固定替换: $output" }
}

enum class PatternSchemaKind {
    Command,
    DataPointer,
    CommandRegex,
}

context(env: Env)
suspend fun exportPatternSchema(
    kind: PatternSchemaKind,
    output: String,
) = withContext(ioDispatcher) {
    val descriptor = when (kind) {
        PatternSchemaKind.Command -> ListSerializer(CommandExtractPattern.serializer()).descriptor
        PatternSchemaKind.DataPointer -> ListSerializer(DataPointerPattern.serializer()).descriptor
        PatternSchemaKind.CommandRegex -> ListSerializer(CommandRegexPattern.serializer()).descriptor
    }
    val schema = SerializationClassJsonSchemaGenerator(json = MCTJson).generateSchema(descriptor)
    writeOutputAtomically(output) { it.writeJson(schema, pretty = GuiSettings.prettyOutput) }
    env.logger.info { "已导出 ${kind.name} JSON Schema: $output" }
}

data class CommandTestResult(
    val content: String,
    val start: Int,
    val endExclusive: Int,
)

context(env: Env)
suspend fun testCommandPatterns(
    input: String,
    patterns: MCTPatternState,
): List<CommandTestResult> = withContext(ioDispatcher) {
    val text = env.fs.read(input.toPath()) { readUtf8() }
    val matches = extractTextFromCommands(text, composePattern(patterns))
        .sortedBy { it.indices.first }
        .map { CommandTestResult(it.content, it.indices.first, it.indices.last + 1) }
    env.logger.info { "命令模式测试找到 ${matches.size} 条文本" }
    matches
}

context(env: Env)
suspend fun combineOfficialLanguages(
    sourceLanguage: String,
    targetLanguage: String,
    output: String,
) = withContext(ioDispatcher) {
    val source = env.fs.read(sourceLanguage.toPath()) { readUtf8() }
        .let { MCTJson.decodeFromString<JsonObject>(it) }
    val target = env.fs.read(targetLanguage.toPath()) { readUtf8() }
        .let { MCTJson.decodeFromString<JsonObject>(it) }
    val terms = target.mapKeys { (key, _) -> source[key]?.jsonPrimitive?.content ?: key }.let(::JsonObject)
    writeOutputAtomically(output) { it.writeJson(terms, pretty = GuiSettings.prettyOutput) }
    env.logger.info { "已合并 ${terms.size} 条官方语言条目: $output" }
}

/** Download the official language resources for one Minecraft version. */
context(env: Env)
suspend fun downloadOfficialLanguages(
    minecraftVersion: String,
    output: String,
    concurrency: Int,
) = withContext(ioDispatcher) {
    require(concurrency in 1..64) { "下载并发数应在 1 到 64 之间" }
    createDownloadClient().use { client ->
        val versionManifest = client.get("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")
            .body<JsonObject>()
        val versionId = if (minecraftVersion == "latest") {
            versionManifest["latest"]!!.jsonObject["release"]!!.jsonPrimitive.content
        } else minecraftVersion
        val version = versionManifest["versions"]!!.jsonArray
            .map { it.jsonObject }
            .firstOrNull { it["id"]!!.jsonPrimitive.content == versionId }
            ?: error("找不到 Minecraft 版本: $versionId")
        val root = output.toPath() / versionId
        val cache = root / "caches"
        env.fs.createDirectories(cache)
        env.logger.info { "正在下载 Minecraft $versionId 的官方语言包" }

        val details = client.get(version["url"]!!.jsonPrimitive.content).body<JsonObject>()
        val clientJar = details["downloads"]!!.jsonObject["client"]!!.jsonObject
        val clientJarPath = cache / "client.jar"
        // Verified against the manifest: an error page or a truncated body delivered with status 200
        // would otherwise be kept as if it were the jar, and only fail much later.
        val clientJarSha1 = clientJar["sha1"]!!.jsonPrimitive.content
        val clientJarActual = client.downloadAndSha1(clientJar["url"]!!.jsonPrimitive.content, clientJarPath)
        check(clientJarActual == clientJarSha1) {
            "client.jar 校验失败：期望 sha1 $clientJarSha1，实际 $clientJarActual"
        }
        copyZipEntry(clientJarPath, "/assets/minecraft/lang/en_us.json", root / "en_us.json")

        val assetIndex = client.get(details["assetIndex"]!!.jsonObject["url"]!!.jsonPrimitive.content).body<JsonObject>()
        val dispatcher = ioDispatcher.limitedParallelism(concurrency)
        coroutineScope {
            assetIndex["objects"]!!.jsonObject.forEach { (name, entry) ->
                val prefix = "minecraft/lang/"
                if (!name.startsWith(prefix)) return@forEach
                val hash = entry.jsonObject["hash"]!!.jsonPrimitive.content
                val url = "https://resources.download.minecraft.net/${hash.take(2)}/$hash"
                launch(dispatcher) {
                    val actual = client.downloadAndSha1(url, root / name.removePrefix(prefix))
                    check(actual == hash) {
                        "${name.removePrefix(prefix)} 校验失败：期望 sha1 $hash，实际 $actual"
                    }
                }
            }
        }
        env.logger.info { "官方语言包已保存到: $root" }
    }
}


