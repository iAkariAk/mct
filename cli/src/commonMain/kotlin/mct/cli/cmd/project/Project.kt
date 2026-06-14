package mct.cli.cmd.project

import arrow.core.raise.Raise
import com.aallam.openai.api.logging.LogLevel
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import mct.*
import mct.cli.BaseCommand
import mct.cli.NotifierHooks
import mct.cli.path
import mct.cli.util.CURRENT_PATH
import mct.dp.backfillDatapack
import mct.dp.compile
import mct.dp.extractFromDatapackRaw
import mct.dp.mcfunction.BuiltinMCFPatterns
import mct.dp.mcfunction.BuiltinMCFunctionDataPatterns
import mct.dp.mcfunction.CommandExtractPattern
import mct.dp.mcjson.BuiltinMCJPatterns
import mct.extra.ai.AiSign
import mct.extra.ai.ChatCompletionCall
import mct.extra.ai.translator.CustomizedPrompts
import mct.extra.ai.translator.TermTable
import mct.extra.ai.translator.Translator
import mct.extra.ai.translator.translate
import mct.kit.TranslationMapping
import mct.kit.exportIntoPool
import mct.kit.replace
import mct.pointer.CustomizedDataPointerPattern
import mct.region.BuiltinRegionPatterns
import mct.region.backfillRegion
import mct.region.extractFromRegion
import mct.util.io.copyToRecursively
import mct.util.io.readJson
import mct.util.io.writeJson
import okio.Path
import okio.Path.Companion.toPath

private const val REGION_CACHE = "region_extractions.json"
private const val DATAPACK_CACHE = "datapack_extractions.json"
private const val POOL = "pool.json"
private const val REGION_REPLACEMENTS = "region_replacements.json"
private const val DATAPACK_REPLACEMENTS = "datapack_replacements.json"

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
            terminal.println(TextColors.red("Failed to copy world: ${e.message ?: "unknown error"}"))
            throw PrintMessage("Failed to copy world: ${e.message ?: "unknown error"}")
        }
        val config = ProjectConfig(
            name = projectName,
        )
        (projectDir / "mct.toml").writeToml(config)
        terminal.println(TextColors.green("Project '$projectName' created at $projectDir"))
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

    fun cache(path: String): Path {
        ensureCache()
        return projectDir / "cache" / path
    }

    fun ensureCache() = fs.createDirectories(projectDir / "cache")
}

private class Update : ProjectCommand("update", "Update extraction pool") {
    context(_: Raise<MCTError>)
    override suspend fun App() {
        fun requirePath(path: String, label: String): Path {
            val p = path.toPath()
            if (!fs.exists(p)) throw PrintMessage("$label pattern file not found: $path")
            return p
        }

        val regionPatterns = projectConfig.patterns.regions.flatMap {
            requirePath(it, "Region").readJson<List<CustomizedDataPointerPattern>>().map { c -> c.compile() }
        }.ifEmpty { BuiltinRegionPatterns }

        val mcfPatterns = projectConfig.patterns.mcfunction.map {
            requirePath(it, "Mcfunction").readJson<CommandExtractPattern>()
        }.let { if (it.isEmpty()) BuiltinMCFPatterns else it.compile() }

        val mcfDataPatterns = projectConfig.patterns.mcfunctionData.flatMap {
            requirePath(it, "Mcfunction data").readJson<List<CustomizedDataPointerPattern>>().map { c -> c.compile() }
        }.ifEmpty { BuiltinMCFunctionDataPatterns }

        val mcjPatterns = projectConfig.patterns.mcjson.flatMap {
            requirePath(it, "Mcjson").readJson<List<CustomizedDataPointerPattern>>().map { c -> c.compile() }
        }.ifEmpty { BuiltinMCJPatterns }

        val w = workspace()

        val mappingFile = projectDir / projectConfig.mappings
        val existingMapping = if (fs.exists(mappingFile)) {
            mappingFile.readJson<TranslationMapping>()
        } else {
            emptyMap()
        }

        val pool = coroutineScope {
            val regionJob = async(Dispatchers.IO) {
                val groups = w.extractFromRegion(
                    regionPatterns, mcfPatterns, mcfDataPatterns
                ).toList()
                cache(REGION_CACHE).writeJson<List<ExtractionGroup>>(groups, projectConfig.prettyJson)
                terminal.println(TextColors.green("Extracted ") + (TextColors.green + TextStyles.bold)("${groups.size}") + TextColors.green(" groups from region"))
                groups
            }
            val datapackJob = async(Dispatchers.IO) {
                val groups = w.extractFromDatapackRaw(
                    mcfPatterns, mcfDataPatterns, mcjPatterns
                ).toList()
                cache(DATAPACK_CACHE).writeJson<List<ExtractionGroup>>(groups, projectConfig.prettyJson)
                terminal.println(TextColors.green("Extracted ") + (TextColors.green + TextStyles.bold)("${groups.size}") + TextColors.green(" groups from datapack"))
                groups
            }
            (regionJob.await() + datapackJob.await()).exportIntoPool(simply = false)
        }
        val missingPool = pool.filter { it !in existingMapping }
        if (missingPool.isNotEmpty()) {
            terminal.println(TextColors.yellow("Missing ") + (TextColors.yellow + TextStyles.bold)("${missingPool.size}") + TextColors.yellow(" items (${pool.size} total extracted)"))
            (projectDir / POOL).writeJson(missingPool, projectConfig.prettyJson)
        } else {
            terminal.println(TextColors.green("No new items found (${pool.size} total extracted, all mapped)"))
        }
    }
}

private class Translate : ProjectCommand("translate", "Translate extractions via AI") {
    context(_: Raise<MCTError>)
    override suspend fun App() {
        val regionFile = cache(REGION_CACHE)
        val datapackFile = cache(DATAPACK_CACHE)

        if (!fs.exists(regionFile) && !fs.exists(datapackFile)) {
            throw PrintMessage("No extractions found in cache. Run 'project update' first.")
        }

        val extractionGroups = mutableListOf<ExtractionGroup>()
        if (fs.exists(regionFile)) {
            extractionGroups += regionFile.readJson<List<ExtractionGroup>>()
            terminal.println(TextColors.green("Loaded region extractions"))
        }
        if (fs.exists(datapackFile)) {
            extractionGroups += datapackFile.readJson<List<ExtractionGroup>>()
            terminal.println(TextColors.green("Loaded datapack extractions"))
        }

        if (extractionGroups.isEmpty()) {
            throw PrintMessage("All cache files are empty. Run 'project update' first.")
        }
        terminal.println(TextColors.cyan("Total ${extractionGroups.size} extraction groups loaded"))

        val mappingFile = projectDir / projectConfig.mappings
        val existingMapping = if (fs.exists(mappingFile)) {
            mappingFile.readJson<TranslationMapping>()
        } else {
            emptyMap()
        }
        terminal.println(TextColors.cyan("Loaded ${existingMapping.size} existing mappings"))

        val termFile = projectDir / projectConfig.terms
        val existingTerms = if (fs.exists(termFile)) {
            termFile.readJson<TermTable>()
        } else {
            emptySet()
        }
        terminal.println(TextColors.cyan("Loaded ${existingTerms.size} existing terms"))

        val ai = projectConfig.ai
        if (ai.token.isBlank() || ai.token == AIConfig.Default.token) {
            throw PrintMessage("AI token not configured. Set [ai.token] in mct.toml")
        }

        val translator = context(env) {
            val call = ChatCompletionCall(
                apiUrl = ai.apiUrl,
                token = ai.token,
                model = ai.model,
                useStreamApi = ai.useStreamApi,
                temperature = ai.temperature,
                logLevel = if (ai.enableHttpLogging) LogLevel.All else LogLevel.None,
            )
            Translator(
                call = call,
                customizedPrompts = CustomizedPrompts(
                    literatureStyle = ai.literatureStyle,
                    targetLanguage = ai.targetLanguage,
                    handleGradientAggressively = ai.handleGradientAggressively,
                ),
                defaultTerms = existingTerms,
                tokenThreshold = ai.tokenThreshold,
            )
        }

        var consumedTokenCount = 0
        NotifierHooks.onAiSign {
            if (it is AiSign.ConsumeToken) {
                consumedTokenCount += it.count
            }
        }

        terminal.println(TextColors.cyan("Starting translation using ${ai.model}..."))
        val mapping = translator.translate(extractionGroups, existingMapping)

        val totalMapping = existingMapping + mapping
        terminal.println(TextColors.green("Translated ") + (TextColors.green + TextStyles.bold)("${mapping.size}") + TextColors.green(" new items (${totalMapping.size} total)"))

        mappingFile.writeJson(totalMapping, projectConfig.prettyJson)
        terminal.println(TextColors.green("Mapping saved to $mappingFile"))

        if (translator.terms.isNotEmpty()) {
            termFile.writeJson(translator.terms, projectConfig.prettyJson)
            terminal.println(TextColors.green("${translator.terms.size} terms saved to $termFile"))
        }

        terminal.println(TextColors.green("Translation complete, consumed ") + (TextColors.green + TextStyles.bold)("$consumedTokenCount") + TextColors.green(" tokens."))
    }
}


private class Build : ProjectCommand("build", "Build translated world") {
    context(_: Raise<MCTError>)
    override suspend fun App() {
        val regionFile = cache(REGION_CACHE)
        val datapackFile = cache(DATAPACK_CACHE)

        if (!fs.exists(regionFile) && !fs.exists(datapackFile)) {
            throw PrintMessage("No extractions found in cache. Run 'project update' first.")
        }

        val mappingFile = projectDir / projectConfig.mappings
        if (!fs.exists(mappingFile)) {
            throw PrintMessage("No mapping found. Run 'project translate' first.")
        }
        val mapping = mappingFile.readJson<TranslationMapping>()
        terminal.println(TextColors.cyan("Loaded mapping with ") + (TextColors.cyan + TextStyles.bold)("${mapping.size}") + TextColors.cyan(" entries"))

        val regionGroups = if (fs.exists(regionFile)) {
            regionFile.readJson<List<ExtractionGroup>>()
        } else {
            emptyList()
        }
        val datapackGroups = if (fs.exists(datapackFile)) {
            datapackFile.readJson<List<ExtractionGroup>>()
        } else {
            emptyList()
        }
        if (regionGroups.isEmpty() && datapackGroups.isEmpty()) {
            throw PrintMessage("All cache files are empty. Run 'project update' first.")
        }

        val regionReplacements = if (regionGroups.isNotEmpty()) {
            @Suppress("UNCHECKED_CAST")
            (regionGroups.replace(mapping) as List<RegionReplacementGroup>).also {
                cache(REGION_REPLACEMENTS).writeJson<List<ReplacementGroup>>(it, projectConfig.prettyJson)
                terminal.println(TextColors.green("Generated ") + (TextColors.green + TextStyles.bold)("${it.size}") + TextColors.green(" region replacement groups"))
            }
        } else {
            emptyList()
        }

        val datapackReplacements = if (datapackGroups.isNotEmpty()) {
            @Suppress("UNCHECKED_CAST")
            (datapackGroups.replace(mapping) as List<DatapackReplacementGroup>).also {
                cache(DATAPACK_REPLACEMENTS).writeJson<List<ReplacementGroup>>(it, projectConfig.prettyJson)
                terminal.println(TextColors.green("Generated ") + (TextColors.green + TextStyles.bold)("${it.size}") + TextColors.green(" datapack replacement groups"))
            }
        } else {
            emptyList()
        }

        val targetDir = projectDir / "build"
        if (!fs.exists(srcDir)) {
            throw PrintMessage("Source world directory not found: $srcDir")
        }
        terminal.println(TextColors.cyan("Copying world to $targetDir ..."))
        if (fs.exists(targetDir)) {
            fs.deleteRecursively(targetDir)
        }
        try {
            context(fs) { srcDir.copyToRecursively(targetDir) }
        } catch (e: Exception) {
            terminal.println(TextColors.red("Failed to copy world: ${e.message ?: "unknown error"}"))
            throw PrintMessage("Failed to copy world: ${e.message ?: "unknown error"}")
        }
        terminal.println(TextColors.green("World copied."))

        val buildWorkspace = workspace(targetDir)

        var hasBackfillErrors = false
        coroutineScope {
            if (regionReplacements.isNotEmpty()) {
                launch(Dispatchers.IO) {
                    try {
                        terminal.println(TextColors.cyan("Backfilling ") + (TextColors.cyan + TextStyles.bold)("${regionReplacements.size}") + TextColors.cyan(" region groups..."))
                        buildWorkspace.backfillRegion(regionReplacements)
                        terminal.println(TextColors.green("Region backfill complete."))
                    } catch (e: Exception) {
                        terminal.println(TextColors.red("Region backfill failed: ${e.message}"))
                        hasBackfillErrors = true
                    }
                }
            }
            if (datapackReplacements.isNotEmpty()) {
                launch(Dispatchers.IO) {
                    try {
                        terminal.println(TextColors.cyan("Backfilling ") + (TextColors.cyan + TextStyles.bold)("${datapackReplacements.size}") + TextColors.cyan(" datapack groups..."))
                        buildWorkspace.backfillDatapack(datapackReplacements)
                        terminal.println(TextColors.green("Datapack backfill complete."))
                    } catch (e: Exception) {
                        terminal.println(TextColors.red("Datapack backfill failed: ${e.message}"))
                        hasBackfillErrors = true
                    }
                }
            }
        }

        if (hasBackfillErrors) {
            terminal.println(TextColors.yellow("Build completed with errors. Check logs above."))
        } else {
            terminal.println(TextColors.green("Build complete. Translated world at ") + (TextColors.green + TextStyles.bold)("$targetDir"))
        }
    }
}
