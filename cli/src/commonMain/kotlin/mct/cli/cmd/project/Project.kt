package mct.cli.cmd.project

import arrow.core.raise.Raise
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import korlibs.io.async.async
import korlibs.io.async.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toList
import mct.*
import mct.cli.BaseCommand
import mct.cli.path
import mct.cli.util.CURRENT_PATH
import mct.dp.backfillDatapack
import mct.dp.compile
import mct.dp.extractFromDatapackRaw
import mct.dp.mcfunction.BuiltinMCFPatterns
import mct.dp.mcfunction.CommandExtractPattern
import mct.extra.ai.ChatCompletionCall
import mct.extra.ai.translator.CustomizedPrompts
import mct.extra.ai.translator.OpenAITranslator
import mct.extra.ai.translator.TermTable
import mct.extra.ai.translator.translate
import mct.kit.TranslationMapping
import mct.kit.exportIntoPool
import mct.kit.replace
import mct.pointer.DataPointerPattern
import mct.region.backfillRegion
import mct.region.extractFromRegion
import mct.util.io.copyToRecursively
import mct.util.io.readJson
import mct.util.io.writeJson
import okio.Path
import okio.Path.Companion.toPath


class Project : SuspendingCliktCommand(name = "project") {
    init {
        subcommands(Init(), Update(), Translate(), Build())
    }

    override fun help(context: Context) = "Project manager"

    override suspend fun run() = Unit
}


private class Init : BaseCommand(name = "init") {
    val projectName by argument(help = "The name of the project")
    val mapDir by option("--from", help = "The path to your map").path().required()

    context(_: Raise<MCTError>)
    override suspend fun App() {
        if ("/" in projectName || "\\" in projectName) {
            throw PrintMessage("name cannot contain / or \\")
        }
        val projectDir = ".".toPath() / projectName
        fs.createDirectories(projectDir)
        if (!fs.exists(mapDir)) {
            throw PrintMessage("Source directory does not exist: $mapDir")
        }
        if (!fs.exists(mapDir / "level.dat")) {
            throw PrintMessage("Source directory is not a valid Minecraft world (level.dat not found): $mapDir")
        }
        val srcTarget = projectDir / "src"
        if (fs.exists(srcTarget)) {
            fs.deleteRecursively(srcTarget)
        }
        try {
            context(fs) { mapDir.copyToRecursively(srcTarget) }
        } catch (e: Exception) {
            e.printStackTrace()
            throw PrintMessage("Failed to copy world: ${e.message ?: "unknown error"}")
        }
        val config = ProjectConfig(
            name = projectName,
        )
        (projectDir / "mct.toml").writeToml(config)
        logger.info { "Project '$projectName' created at $projectDir" }
    }
}

private abstract class ProjectCommand(name: String? = null, help: String? = null) : BaseCommand(name, help) {
    val projectDir = Path.CURRENT_PATH
    val projectConfig by lazy {
        val toml = projectDir / "mct.toml"
        if (!fs.exists(toml)) {
            throw PrintMessage("Not a project directory (mct.toml not found). Run 'project init' first.")
        }
        toml.readToml<ProjectConfig>()
    }
    val srcDir = projectDir / "src"

    context(_: Raise<MCTError>)
    fun workspace(dir: Path = srcDir): MCTWorkspace {
        if (!fs.exists(dir / "level.dat")) {
            throw PrintMessage("Source directory is not a valid Minecraft world: $dir")
        }
        return MCTWorkspace(dir, env)
    }

    fun cache(path: String) = projectDir / "cache" / path
    fun ensureCache() = fs.createDirectories(projectDir / "cache")
}

private class Update : ProjectCommand("update", "Update extraction pool") {
    context(_: Raise<MCTError>)
    override suspend fun App() {
        val regionPatterns = projectConfig.patterns.regions.map {
            val p = it.toPath()
            if (!fs.exists(p)) throw PrintMessage("Region pattern file not found: $it")
            p.readJson<DataPointerPattern>()
        }.ifEmpty { null }

        val mcfPatterns = projectConfig.patterns.mcfunction.map {
            val p = it.toPath()
            if (!fs.exists(p)) throw PrintMessage("Mcfunction pattern file not found: $it")
            p.readJson<CommandExtractPattern>()
        }.let { if (it.isEmpty()) BuiltinMCFPatterns else it.compile() }

        val mcfDataPatterns = projectConfig.patterns.mcfunctionData.map {
            val p = it.toPath()
            if (!fs.exists(p)) throw PrintMessage("Mcfunction data pattern file not found: $it")
            p.readJson<DataPointerPattern>()
        }.ifEmpty { null }

        val mcjPatterns = projectConfig.patterns.mcjson.map {
            val p = it.toPath()
            if (!fs.exists(p)) throw PrintMessage("Mcjson pattern file not found: $it")
            p.readJson<DataPointerPattern>()
        }.ifEmpty { null }

        ensureCache()

        val w = workspace()
        val regionJob = async(Dispatchers.IO) {
            val groups = w.extractFromRegion(
                regionPatterns, mcfPatterns, mcfDataPatterns
            ).toList()
            cache("region_extractions.json").writeJson(groups, projectConfig.prettyJson)
            logger.info { "Extracted ${groups.size} groups from region" }
            groups
        }
        val datapackJob = async(Dispatchers.IO) {
            val groups = w.extractFromDatapackRaw(
                mcfPatterns, mcfDataPatterns, mcjPatterns
            ).toList()
            cache("datapack_extractions.json").writeJson(groups, projectConfig.prettyJson)
            logger.info { "Extracted ${groups.size} groups from datapack" }
            groups
        }

        val mappingFile = projectDir / projectConfig.mappings
        val existingMapping = if (fs.exists(mappingFile)) {
            mappingFile.readJson<TranslationMapping>()
        } else {
            emptyMap()
        }

        val pool = (regionJob.await() + datapackJob.await()).exportIntoPool(false)
        val missingPool = pool.filter { it !in existingMapping }
        if (missingPool.isNotEmpty()) {
            env.logger.info { "Missing ${missingPool.size} items (${pool.size} total extracted)" }
            (projectDir / "pool.json").writeJson(missingPool, projectConfig.prettyJson)
        } else {
            env.logger.info { "No new items found (${pool.size} total extracted, all mapped)" }
        }
    }
}

private class Translate : ProjectCommand("translate", "Translate extractions via AI") {
    context(_: Raise<MCTError>)
    override suspend fun App() {
        val regionFile = cache("region_extractions.json")
        val datapackFile = cache("datapack_extractions.json")

        if (!fs.exists(regionFile) && !fs.exists(datapackFile)) {
            throw PrintMessage("No extractions found in cache. Run 'project update' first.")
        }

        val extractionGroups = mutableListOf<ExtractionGroup>()
        if (fs.exists(regionFile)) {
            extractionGroups += regionFile.readJson<List<ExtractionGroup>>()
            logger.info { "Loaded region extractions" }
        }
        if (fs.exists(datapackFile)) {
            extractionGroups += datapackFile.readJson<List<ExtractionGroup>>()
            logger.info { "Loaded datapack extractions" }
        }

        if (extractionGroups.isEmpty()) {
            throw PrintMessage("All cache files are empty. Run 'project update' first.")
        }
        logger.info { "Total ${extractionGroups.size} extraction groups loaded" }

        val mappingFile = projectDir / projectConfig.mappings
        val existingMapping = if (fs.exists(mappingFile)) {
            mappingFile.readJson<TranslationMapping>()
        } else {
            emptyMap()
        }
        logger.info { "Loaded ${existingMapping.size} existing mappings" }

        val termFile = projectDir / projectConfig.terms
        val existingTerms = if (fs.exists(termFile)) {
            termFile.readJson<TermTable>()
        } else {
            emptySet()
        }
        logger.info { "Loaded ${existingTerms.size} existing terms" }

        val ai = projectConfig.ai
        if (ai.token.isNullOrBlank()) {
            throw PrintMessage("AI token not configured. Set [ai.token] in mct.toml")
        }

        val translator = context(env) {
            val call = ChatCompletionCall(
                apiUrl = ai.apiUrl,
                token = ai.token,
                model = ai.model,
                useStreamApi = ai.useStreamApi,
            )
            OpenAITranslator(
                call = call,
                customizedPrompts = CustomizedPrompts(
                    literatureStyle = ai.literatureStyle ?: CustomizedPrompts.literatureStyle,
                    targetLanguage = ai.targetLanguage ?: CustomizedPrompts.targetLanguage,
                ),
                defaultTerms = existingTerms,
                tokenThreshold = ai.tokenThreshold,
            )
        }

        logger.info { "Starting translation using ${ai.model}..." }
        val mapping = translator.translate(extractionGroups, existingMapping)

        val totalMapping = existingMapping + mapping
        logger.info { "Translated ${mapping.size} new items (${totalMapping.size} total)" }

        ensureCache()
        mappingFile.writeJson(totalMapping, projectConfig.prettyJson)
        logger.info { "Mapping saved to $mappingFile" }

        if (translator.terms.isNotEmpty()) {
            termFile.writeJson(translator.terms, projectConfig.prettyJson)
            logger.info { "${translator.terms.size} terms saved to $termFile" }
        }

        logger.info { "Translation complete." }
    }
}


private class Build : ProjectCommand("build", "Build translated world") {
    context(_: Raise<MCTError>)
    override suspend fun App() {
        val regionFile = cache("region_extractions.json")
        val datapackFile = cache("datapack_extractions.json")

        if (!fs.exists(regionFile) && !fs.exists(datapackFile)) {
            throw PrintMessage("No extractions found in cache. Run 'project update' first.")
        }

        val mappingFile = projectDir / projectConfig.mappings
        if (!fs.exists(mappingFile)) {
            throw PrintMessage("No mapping found. Run 'project translate' first.")
        }
        val mapping = mappingFile.readJson<TranslationMapping>()
        logger.info { "Loaded mapping with ${mapping.size} entries" }
        val allGroups = mutableListOf<ExtractionGroup>()
        if (fs.exists(regionFile)) {
            allGroups += regionFile.readJson<List<ExtractionGroup>>()
        }
        if (fs.exists(datapackFile)) {
            allGroups += datapackFile.readJson<List<ExtractionGroup>>()
        }
        if (allGroups.isEmpty()) {
            throw PrintMessage("All cache files are empty. Run 'project update' first.")
        }

        ensureCache()
        val replacements = allGroups.replace(mapping)
        cache("replacements.json").writeJson(replacements, projectConfig.prettyJson)
        logger.info { "Generated ${replacements.size} replacement groups" }

        val regionGroups = replacements.filterIsInstance<RegionReplacementGroup>()
        val datapackGroups = replacements.filterIsInstance<DatapackReplacementGroup>()
        logger.info { "Region: ${regionGroups.size}, Datapack: ${datapackGroups.size} replacement groups" }

        val targetDir = projectDir / "build"
        if (!fs.exists(srcDir)) {
            throw PrintMessage("Source world directory not found: $srcDir")
        }
        logger.info { "Copying world to $targetDir ..." }
        if (fs.exists(targetDir)) {
            fs.deleteRecursively(targetDir)
        }
        context(fs) { srcDir.copyToRecursively(targetDir) }
        logger.info { "World copied." }

        val buildWorkspace = workspace(targetDir)

        coroutineScope {
            if (regionGroups.isNotEmpty()) {
                launch(Dispatchers.IO) {
                    logger.info { "Backfilling ${regionGroups.size} region groups..." }
                    buildWorkspace.backfillRegion(regionGroups)
                    logger.info { "Region backfill complete." }
                }
            }
            if (datapackGroups.isNotEmpty()) {
                launch(Dispatchers.IO) {
                    logger.info { "Backfilling ${datapackGroups.size} datapack groups..." }
                    buildWorkspace.backfillDatapack(datapackGroups)
                    logger.info { "Datapack backfill complete." }
                }
            }
        }

        logger.info { "Build complete. Translated world at $targetDir" }
    }
}
