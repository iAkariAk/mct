package mct.gui.services

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.schema.generator.json.serialization.SerializationClassJsonSchemaGenerator
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mct.Env
import mct.MCTPattern
import mct.command.*
import mct.dp.compile
import mct.gui.model.GuiSettings
import mct.kit.TranslationMapping
import mct.kit.TranslationPool
import mct.kit.exportIntoPool
import mct.model.patch.*
import mct.mtl.MTLX
import mct.mtl.generateMTLXTemplate
import mct.mtl.translateByMTLX
import mct.pointer.CustomizedDataPointerPattern
import mct.pointer.DataPointerPattern
import mct.serializer.MCTJson
import mct.util.io.writeJson
import mct.util.io.writeText
import okio.Path.Companion.toPath
import okio.buffer
import okio.openZip
import okio.use

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
) = withContext(Dispatchers.IO) {
    val content = env.fs.read(input.toPath()) { readUtf8() }
    val groups = when (kind) {
        "region" -> MCTJson.decodeFromString<List<RegionExtractionGroup>>(content)
        "datapack" -> MCTJson.decodeFromString<List<DatapackExtractionGroup>>(content)
        else -> error("未知提取类型: $kind")
    }
    val pool = groups.exportIntoPool(simply)
    output.toPath().writeJson(pool, pretty = GuiSettings.prettyOutput)
    env.logger.info { "已将 ${groups.size} 个分组导出为 ${pool.size} 条文本: $output" }
}

context(env: Env)
suspend fun unflattenTextPool(
    input: String,
    mapping: String,
    output: String,
) = withContext(Dispatchers.IO) {
    val groups = env.fs.read(input.toPath()) { readUtf8() }
        .let { MCTJson.decodeFromString<List<ExtractionGroup>>(it) }
    val translations = env.fs.read(mapping.toPath()) { readUtf8() }
        .let { MCTJson.decodeFromString<TranslationMapping>(it) }
    val replacements = groups.replace(translations)
    output.toPath().writeJson(replacements, pretty = GuiSettings.prettyOutput)
    env.logger.info { "已将 ${translations.size} 条映射应用到 ${replacements.size} 个替换分组: $output" }
}

context(env: Env)
suspend fun generateMtlxTemplate(
    poolPath: String,
    output: String,
) = withContext(Dispatchers.IO) {
    val pool = env.fs.read(poolPath.toPath()) { readUtf8() }
        .let { MCTJson.decodeFromString<TranslationPool>(it) }
    val template = pool.generateMTLXTemplate()
    output.toPath().writeText(template.render())
    env.logger.info { "已从 ${pool.size} 条文本生成 MTLX 模板: $output" }
}

context(env: Env)
suspend fun translateByMtlx(
    mtlxPath: String,
    poolPath: String,
    output: String,
) = withContext(Dispatchers.IO) {
    val mtlx = env.fs.read(mtlxPath.toPath()) { readUtf8() }.let(MTLX::fromString)
    val pool = env.fs.read(poolPath.toPath()) { readUtf8() }
        .let { MCTJson.decodeFromString<TranslationPool>(it) }
    val mapping = pool.translateByMTLX(mtlx)
    output.toPath().writeJson(mapping, pretty = GuiSettings.prettyOutput)
    val translated = mapping.count { it.value != null }
    env.logger.info { "MTLX 已匹配 $translated/${pool.size} 条文本: $output" }
}

context(env: Env)
suspend fun replaceAllExtractions(
    input: String,
    output: String,
    replacement: String,
) = withContext(Dispatchers.IO) {
    val groups = env.fs.read(input.toPath()) { readUtf8() }
        .let { MCTJson.decodeFromString<List<ExtractionGroup>>(it) }
    val replacements = groups.replaceSimply { replacement }
    output.toPath().writeJson(replacements, pretty = GuiSettings.prettyOutput)
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
) = withContext(Dispatchers.IO) {
    val descriptor = when (kind) {
        PatternSchemaKind.Command -> CommandExtractPattern.serializer().descriptor
        PatternSchemaKind.DataPointer -> CustomizedDataPointerPattern.serializer().descriptor
        PatternSchemaKind.CommandRegex -> CommandRegexPattern.serializer().descriptor
    }
    val schema = SerializationClassJsonSchemaGenerator(json = MCTJson).generateSchema(descriptor)
    output.toPath().writeJson(schema, pretty = GuiSettings.prettyOutput)
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
    commandPatternPath: String?,
    commandDataPatternPath: String?,
    noBuiltin: Boolean,
): List<CommandTestResult> = withContext(Dispatchers.IO) {
    val text = env.fs.read(input.toPath()) { readUtf8() }
    val extraCommand = commandPatternPath?.takeIf(String::isNotBlank)
        ?.let { path -> env.fs.read(path.toPath()) { readUtf8() } }
        ?.let { MCTJson.decodeFromString<List<CommandExtractPattern>>(it) }
    val extraCommandData = commandDataPatternPath?.takeIf(String::isNotBlank)
        ?.let { path -> env.fs.read(path.toPath()) { readUtf8() } }
        ?.let { MCTJson.decodeFromString<List<CustomizedDataPointerPattern>>(it).map { pattern -> pattern.compile() } }
        .orEmpty()
    val command = extraCommand?.compile(!noBuiltin) ?: BuiltinCommandPatterns
    val commandData: List<DataPointerPattern> = if (noBuiltin) extraCommandData
    else BuiltinCommandDataPatterns + extraCommandData
    val matches = extractTextFromCommands(text, MCTPattern(command = command, commandData = commandData))
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
) = withContext(Dispatchers.IO) {
    val source = env.fs.read(sourceLanguage.toPath()) { readUtf8() }
        .let { MCTJson.decodeFromString<JsonObject>(it) }
    val target = env.fs.read(targetLanguage.toPath()) { readUtf8() }
        .let { MCTJson.decodeFromString<JsonObject>(it) }
    val terms = target.mapKeys { (key, _) -> source[key]?.jsonPrimitive?.content ?: key }.let(::JsonObject)
    output.toPath().writeJson(terms, pretty = GuiSettings.prettyOutput)
    env.logger.info { "已合并 ${terms.size} 条官方语言条目: $output" }
}

/** Download the official language resources for one Minecraft version. */
context(env: Env)
suspend fun downloadOfficialLanguages(
    minecraftVersion: String,
    output: String,
    concurrency: Int,
) = withContext(Dispatchers.IO) {
    require(concurrency in 1..64) { "下载并发数应在 1 到 64 之间" }
    HttpClient(CIO) {
        install(ContentNegotiation) { json() }
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 60_000
            socketTimeoutMillis = 60_000
        }
        install(HttpRequestRetry) {
            maxRetries = 3
            exponentialDelay()
        }
    }.use { client ->
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
        client.downloadTo(clientJar["url"]!!.jsonPrimitive.content, clientJarPath)
        env.fs.openZip(clientJarPath).use { zip ->
            zip.source("/assets/minecraft/lang/en_us.json".toPath()).buffer().use { source ->
                env.fs.sink(root / "en_us.json").use(source::readAll)
            }
        }

        val assetIndex = client.get(details["assetIndex"]!!.jsonObject["url"]!!.jsonPrimitive.content).body<JsonObject>()
        val dispatcher = Dispatchers.IO.limitedParallelism(concurrency)
        coroutineScope {
            assetIndex["objects"]!!.jsonObject.forEach { (name, entry) ->
                val prefix = "minecraft/lang/"
                if (!name.startsWith(prefix)) return@forEach
                val hash = entry.jsonObject["hash"]!!.jsonPrimitive.content
                val url = "https://resources.download.minecraft.net/${hash.take(2)}/$hash"
                launch(dispatcher) {
                    client.downloadTo(url, root / name.removePrefix(prefix))
                }
            }
        }
        env.logger.info { "官方语言包已保存到: $root" }
    }
}

context(env: Env)
private suspend fun HttpClient.downloadTo(url: String, target: okio.Path) {
    val bytes = get(url).body<ByteArray>()
    env.fs.write(target) { write(bytes) }
}
