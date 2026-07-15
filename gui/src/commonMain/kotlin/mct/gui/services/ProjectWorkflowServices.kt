package mct.gui.services

import arrow.core.raise.either
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import mct.Env
import mct.extra.ai.translator.MapInfo
import mct.gui.model.GuiSettings
import mct.kit.exportIntoPool
import mct.model.patch.ExtractionGroup
import mct.util.io.copyToRecursively
import mct.util.io.writeJson
import okio.Path
import okio.Path.Companion.toPath

private const val PROJECT_FILE = "mct-gui-project.json"
private const val CACHE_DIR = "cache"
private const val REGION_EXTRACTIONS = "region-extractions.json"
private const val DATAPACK_EXTRACTIONS = "datapack-extractions.json"
private const val EXTRACTIONS = "extractions.json"
private const val TEXT_POOL = "text-pool.json"
private const val TERMS = "terms.json"
private const val MAPPINGS = "mappings.json"
private const val REPLACEMENTS = "replacements.json"

@Serializable
data class GuiProjectConfig(
    val name: String,
    val source: String,
    val target: String,
)

private fun projectFile(projectDir: Path) = projectDir / PROJECT_FILE
private fun cacheDir(projectDir: Path) = projectDir / CACHE_DIR
private fun cacheFile(projectDir: Path, name: String) = cacheDir(projectDir) / name

context(env: Env)
suspend fun initialiseProject(
    projectDirectory: String,
    name: String,
    source: String,
    target: String,
) = withContext(Dispatchers.IO) {
    val root = projectDirectory.toPath()
    require(source.isNotBlank()) { "请选择源存档目录" }
    require(target.isNotBlank()) { "请选择构建输出目录" }
    env.fs.createDirectories(root)
    env.fs.createDirectories(cacheDir(root))
    projectFile(root).writeJson(
        GuiProjectConfig(name.ifBlank { root.name }, source, target),
        pretty = GuiSettings.prettyOutput,
    )
    env.logger.info { "项目已初始化: ${projectFile(root)}" }
}

context(env: Env)
suspend fun updateProject(projectDirectory: String) = withContext(Dispatchers.IO) {
    val root = projectDirectory.toPath()
    val config = loadProject(root)
    env.fs.createDirectories(cacheDir(root))
    runExtraction(config.source, cacheFile(root, REGION_EXTRACTIONS).toString(), "region", disableFilter = false)
    runExtraction(config.source, cacheFile(root, DATAPACK_EXTRACTIONS).toString(), "datapack", disableFilter = false)

    val region = readGroups(cacheFile(root, REGION_EXTRACTIONS))
    val datapack = readGroups(cacheFile(root, DATAPACK_EXTRACTIONS))
    val all = region + datapack
    cacheFile(root, EXTRACTIONS).writeJson(all, pretty = GuiSettings.prettyOutput)
    cacheFile(root, TEXT_POOL).writeJson(all.exportIntoPool(simply = false), pretty = GuiSettings.prettyOutput)
    env.logger.info { "项目文本已更新：${region.size} 个 Region 分组，${datapack.size} 个 Datapack 分组。" }
}

context(env: Env)
suspend fun extractProjectTerms(
    projectDirectory: String,
    clientManager: ClientManager,
    targetLanguage: String,
    literatureStyle: String,
    mapInfo: MapInfo,
    extraPrompts: String?,
) {
    val root = projectDirectory.toPath()
    runTermExtraction(
        clientManager = clientManager,
        input = cacheFile(root, EXTRACTIONS).toString(),
        output = cacheFile(root, TERMS).toString(),
        termPath = cacheFile(root, TERMS).takeIf { env.fs.exists(it) }?.toString(),
        targetLanguage = targetLanguage,
        literatureStyle = literatureStyle,
        mapInfo = mapInfo,
        extraPrompts = extraPrompts,
    )
}

context(env: Env)
suspend fun translateProject(
    projectDirectory: String,
    clientManager: ClientManager,
    apiUrl: String?,
    token: String,
    model: String,
    targetLanguage: String,
    literatureStyle: String,
    mapInfo: MapInfo,
    extraPrompts: String?,
    handleGradientAggressively: Boolean,
) {
    val root = projectDirectory.toPath()
    either {
        runTranslation(
        input = cacheFile(root, EXTRACTIONS).toString(),
        output = cacheFile(root, REPLACEMENTS).toString(),
        mappingOutput = cacheFile(root, MAPPINGS).toString(),
        termOutput = cacheFile(root, TERMS).toString(),
        apiUrl = apiUrl,
        token = token,
        model = model,
        termPath = cacheFile(root, TERMS).takeIf { env.fs.exists(it) }?.toString(),
        cachesPath = cacheFile(root, MAPPINGS).takeIf { env.fs.exists(it) }?.toString(),
        literatureStyle = literatureStyle,
        targetLanguage = targetLanguage,
        handleGradientAggressively = handleGradientAggressively,
        mapInfo = mapInfo,
        extraPrompts = extraPrompts,
        temperature = GuiSettings.temperature,
        onFailure = { error -> env.logger.error { error.message } },
            clientManager = clientManager,
        )
    }.onLeft { error -> env.logger.error { error.message } }
}

context(env: Env)
suspend fun buildProject(projectDirectory: String) = withContext(Dispatchers.IO) {
    val root = projectDirectory.toPath()
    val config = loadProject(root)
    val source = config.source.toPath()
    val target = config.target.toPath()
    require(source != target) { "构建输出目录不能与源存档目录相同" }
    require(env.fs.exists(cacheFile(root, REPLACEMENTS))) { "尚未生成替换文件，请先执行翻译" }
    with(env.fs) { source.copyToRecursively(target) }
    val replacements = cacheFile(root, REPLACEMENTS).toString()
    runBackfill(env, target.toString(), replacements, "region")
    runBackfill(env, target.toString(), replacements, "datapack")
    env.logger.info { "项目构建完成: $target" }
}

context(env: Env)
private fun loadProject(projectDirectory: Path): GuiProjectConfig {
    val file = projectFile(projectDirectory)
    require(env.fs.exists(file)) { "未找到项目配置，请先初始化项目" }
    return env.fs.read(file) { readUtf8() }.let { mct.serializer.MCTJson.decodeFromString(it) }
}

context(env: Env)
private fun readGroups(file: Path): List<ExtractionGroup> =
    if (env.fs.exists(file)) env.fs.read(file) { readUtf8() }
        .let { mct.serializer.MCTJson.decodeFromString(it) }
    else emptyList()
