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
import com.github.ajalt.mordant.rendering.TextAlign
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.bold
import com.github.ajalt.mordant.widgets.Panel
import com.github.ajalt.mordant.widgets.Text
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import mct.MCTError
import mct.MCTPattern
import mct.MCTWorkspace
import mct.cli.BaseCommand
import mct.cli.NotifierHooks
import mct.cli.panic
import mct.cli.path
import mct.cli.util.CURRENT_PATH
import mct.command.BuiltinMCFPatterns
import mct.command.BuiltinMCFunctionDataPatterns
import mct.command.CommandExtractPattern
import mct.command.RegexPattern
import mct.dp.backfillDatapack
import mct.dp.compile
import mct.dp.extractFromDatapack
import mct.dp.mcjson.BuiltinMCJPatterns
import mct.extra.ai.AiSign
import mct.extra.ai.ChatCompletionCall
import mct.extra.ai.translator.CustomizedPrompts
import mct.extra.ai.translator.TermTable
import mct.extra.ai.translator.Translator
import mct.extra.ai.translator.translate
import mct.kit.TranslationMapping
import mct.kit.exportIntoPool
import mct.model.patch.*
import mct.nbt.BuiltinNbtPatterns
import mct.pointer.CustomizedDataPointerPattern
import mct.region.backfillRegion
import mct.region.extractFromRegion
import mct.util.io.copyToRecursively
import mct.util.io.readJson
import mct.util.io.writeJson
import okio.Path
import okio.Path.Companion.toPath

private const val REGION_CACHE = "region_extractions.json"
private const val DATAPACK_CACHE = "datapack_extractions.json"
private const val MISSING = "missing.json"
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
            panic("name cannot contain / or \\")
        }
        val projectDir = ".".toPath() / projectName
        fs.createDirectories(projectDir)
        if (!fs.exists(mapDir)) {
            panic("Source directory does not exist: $mapDir")
        }
        if (!fs.exists(mapDir / "level.dat")) {
            panic("Source directory is not a valid Minecraft world (level.dat not found): $mapDir")
        }
        val srcTarget = projectDir / "src"
        if (fs.exists(srcTarget)) {
            fs.deleteRecursively(srcTarget)
        }
        try {
            context(fs) { mapDir.copyToRecursively(srcTarget) }
        } catch (e: Exception) {
            terminal.println(red("Failed to copy world: ${e.message ?: "unknown error"}"))
            panic("Failed to copy world: ${e.message ?: "unknown error"}")
        }
        val config = ProjectConfig(
            name = projectName,
        )
        (projectDir / "mct.toml").writeToml(config)
        terminal.println(green("Project '$projectName' created at $projectDir"))
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
            panic("Source directory is not a valid Minecraft world: $dir")
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
            if (!fs.exists(p)) panic("$label pattern file not found: $path")
            return p
        }

        val regionPatterns = projectConfig.patterns.regions.flatMap {
            requirePath(it, "Region").readJson<List<CustomizedDataPointerPattern>>().map { c -> c.compile() }
        }.ifEmpty { BuiltinNbtPatterns }

        val mcfPatterns = projectConfig.patterns.mcfunction.map {
            requirePath(it, "MCFunction").readJson<CommandExtractPattern>()
        }.let { if (it.isEmpty()) BuiltinMCFPatterns else it.compile() }

        val mcfDataPatterns = projectConfig.patterns.mcfunctionData.flatMap {
            requirePath(it, "MCFunction data").readJson<List<CustomizedDataPointerPattern>>().map { c -> c.compile() }
        }.ifEmpty { BuiltinMCFunctionDataPatterns }

        val mcjPatterns = projectConfig.patterns.mcjson.flatMap {
            requirePath(it, "MCJson").readJson<List<CustomizedDataPointerPattern>>().map { c -> c.compile() }
        }.ifEmpty { BuiltinMCJPatterns }

        val mcfunctionRegexPatterns = projectConfig.patterns.mcfunctionRegex.flatMap {
            requirePath(it, "MCFunction regex").readJson<List<RegexPattern>>()
        }

        val w = workspace()

        val mappingFile = projectDir / projectConfig.mappings
        val existingMapping = if (fs.exists(mappingFile)) {
            mappingFile.readJson<TranslationMapping>()
        } else {
            emptyMap()
        }

        val pool = coroutineScope {
            val regionJob = async(Dispatchers.IO) {
                val groups = w.extractFromRegion(MCTPattern(
                    nbt = regionPatterns,
                    mcfunction = mcfPatterns,
                    mcfunctionData = mcfDataPatterns,
                    mcfunctionRegex = mcfunctionRegexPatterns
                )).toList()
                cache(REGION_CACHE).writeJson<List<ExtractionGroup>>(groups, projectConfig.prettyJson)
                terminal.println(
                    green("Extracted ") + (green + bold)("${groups.size}") + green(
                        " groups from region"
                    )
                )
                groups
            }
            val datapackJob = async(Dispatchers.IO) {
                val groups = w.extractFromDatapack(MCTPattern(
                    mcfunction = mcfPatterns,
                    mcfunctionData = mcfDataPatterns,
                    mcjson = mcjPatterns,
                    mcfunctionRegex = mcfunctionRegexPatterns
                )).toList()
                cache(DATAPACK_CACHE).writeJson<List<ExtractionGroup>>(groups, projectConfig.prettyJson)
                terminal.println(
                    green("Extracted ") + (green + bold)("${groups.size}") + green(
                        " groups from datapack"
                    )
                )
                groups
            }
            (regionJob.await() + datapackJob.await()).exportIntoPool(simply = false)
        }
        val missingPool = pool.filter { it !in existingMapping }
        if (missingPool.isNotEmpty()) {
            terminal.println(
                yellow("Missing ") + (yellow + bold)("${missingPool.size}") + yellow(
                    " items (${pool.size} total extracted)"
                )
            )
            (projectDir / MISSING).writeJson(missingPool, projectConfig.prettyJson)
        } else {
            terminal.println(green("No new items found (${pool.size} total extracted, all mapped)"))
        }
    }
}

private class Translate : ProjectCommand("translate", "Translate extractions via AI") {
    context(_: Raise<MCTError>)
    override suspend fun App() {
        val regionFile = cache(REGION_CACHE)
        val datapackFile = cache(DATAPACK_CACHE)

        if (!fs.exists(regionFile) && !fs.exists(datapackFile)) {
            panic("No extractions found in cache. Run 'project update' first.")
        }

        val extractionGroups = mutableListOf<ExtractionGroup>()
        if (fs.exists(regionFile)) {
            extractionGroups += regionFile.readJson<List<ExtractionGroup>>()
            terminal.println(green("Loaded region extractions"))
        }
        if (fs.exists(datapackFile)) {
            extractionGroups += datapackFile.readJson<List<ExtractionGroup>>()
            terminal.println(green("Loaded datapack extractions"))
        }

        if (extractionGroups.isEmpty()) {
            panic("All cache files are empty. Run 'project update' first.")
        }
        terminal.println(cyan("Total ${extractionGroups.size} extraction groups loaded"))

        val mappingFile = projectDir / projectConfig.mappings
        val existingMapping = if (fs.exists(mappingFile)) {
            mappingFile.readJson<TranslationMapping>()
        } else {
            emptyMap()
        }
        terminal.println(cyan("Loaded ${existingMapping.size} existing mappings"))

        val termFile = projectDir / projectConfig.terms
        val existingTerms = if (fs.exists(termFile)) {
            termFile.readJson<TermTable>()
        } else {
            emptySet()
        }
        terminal.println(cyan("Loaded ${existingTerms.size} existing terms"))

        val ai = projectConfig.ai
        if (ai.token.isBlank() || ai.token == AIConfig.Default.token) {
            panic("AI token not configured. Set [ai.token] in mct.toml")
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
                concurrency = ai.concurrency
            )
        }

        var totalConsumedTokenCount = 0
        val thinkings = mutableMapOf<Int, StringBuilder>()
        fun outputThinking(id: Int, thinking: String, consumedTokenCount: Int) {
            terminal.println(
                Panel(
                    title = Text(blue("Thinking ($id)")),
                    content = Text(thinking),
                    bottomTitle = Text(yellow("Consume $consumedTokenCount tokens")),
                    bottomTitleAlign = TextAlign.RIGHT,
                )
            )
        }
        NotifierHooks.onAiSign {
            when (it) {
                is AiSign.Reasoning -> {
                    val sb = thinkings.getOrPut(it.id) { StringBuilder() }
                    if (it.terminated) {
                        val content = sb.toString()
                        if (content.isNotEmpty()) {
                            outputThinking(it.id, content, it.consumeTokenCount!!)
                        }
                        thinkings.remove(it.id)
                    } else {
                        sb.append(it.reasoningContent)
                    }
                }

                is AiSign.ConsumeToken -> {
                    totalConsumedTokenCount += it.count
                }
            }
        }

        terminal.println(cyan("Starting translation using ${bold(ai.model)} model and ${bold(ai.concurrency.toString())} concurrency..."))
        val mapping = translator.translate(extractionGroups, existingMapping, ai.concurrentByKind) { terms, salvaged ->
            mappingFile.writeJson(existingMapping + salvaged, projectConfig.prettyJson)
            terminal.println(green("Mapping saved to $mappingFile"))

            if (translator.terms.isNotEmpty()) {
                termFile.writeJson(translator.terms, projectConfig.prettyJson)
            }

            terminal.println(
                red(
                    "Translation was cancelled; salvaged ${bold(terms.size.toString())} terms and ${
                        bold(salvaged.size.toString())
                    } translated items."
                )
            )
        }

        val totalMapping = existingMapping + mapping
        terminal.println(
            green("Translated ") + (green + bold)("${mapping.size}") + green(
                " new items (${totalMapping.size} total)"
            )
        )

        mappingFile.writeJson(totalMapping, projectConfig.prettyJson)
        terminal.println(green("Mapping saved to $mappingFile"))

        if (translator.terms.isNotEmpty()) {
            termFile.writeJson(translator.terms, projectConfig.prettyJson)
            terminal.println(green("${translator.terms.size} terms saved to $termFile"))
        }

        terminal.println(
            green("Translation complete, consumed ") + (green + bold)("$totalConsumedTokenCount") + green(
                " tokens."
            )
        )
    }
}


private class Build : ProjectCommand("build", "Build translated world") {
    context(_: Raise<MCTError>)
    override suspend fun App() {
        val regionFile = cache(REGION_CACHE)
        val datapackFile = cache(DATAPACK_CACHE)

        if (!fs.exists(regionFile) && !fs.exists(datapackFile)) {
            panic("No extractions found in cache. Run 'project update' first.")
        }

        val mappingFile = projectDir / projectConfig.mappings
        if (!fs.exists(mappingFile)) {
            panic("No mapping found. Run 'project translate' first.")
        }
        val mapping = mappingFile.readJson<TranslationMapping>()
        terminal.println(
            cyan("Loaded mapping with ") + (cyan + bold)("${mapping.size}") + cyan(
                " entries"
            )
        )

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
            panic("All cache files are empty. Run 'project update' first.")
        }

        val regionReplacements = if (regionGroups.isNotEmpty()) {
            @Suppress("UNCHECKED_CAST") (regionGroups.replace(mapping) as List<RegionReplacementGroup>).also {
                cache(REGION_REPLACEMENTS).writeJson<List<ReplacementGroup>>(it, projectConfig.prettyJson)
                terminal.println(
                    green("Generated ") + (green + bold)("${it.size}") + green(
                        " region replacement groups"
                    )
                )
            }
        } else {
            emptyList()
        }

        val datapackReplacements = if (datapackGroups.isNotEmpty()) {
            @Suppress("UNCHECKED_CAST") (datapackGroups.replace(mapping) as List<DatapackReplacementGroup>).also {
                cache(DATAPACK_REPLACEMENTS).writeJson<List<ReplacementGroup>>(it, projectConfig.prettyJson)
                terminal.println(
                    green("Generated ") + (green + bold)("${it.size}") + green(
                        " datapack replacement groups"
                    )
                )
            }
        } else {
            emptyList()
        }

        val targetDir = projectDir / "build"
        if (!fs.exists(srcDir)) {
            panic("Source world directory not found: $srcDir")
        }
        terminal.println(cyan("Copying world to $targetDir ..."))
        if (fs.exists(targetDir)) {
            fs.deleteRecursively(targetDir)
        }
        try {
            context(fs) { srcDir.copyToRecursively(targetDir) }
        } catch (e: Exception) {
            terminal.println(red("Failed to copy world: ${e.message ?: "unknown error"}"))
            panic("Failed to copy world: ${e.message ?: "unknown error"}")
        }
        terminal.println(green("World copied."))

        val buildWorkspace = workspace(targetDir)

        var hasBackfillErrors = false
        coroutineScope {
            if (regionReplacements.isNotEmpty()) {
                launch(Dispatchers.IO) {
                    try {
                        terminal.println(
                            cyan("Backfilling ") + (cyan + bold)("${regionReplacements.size}") + cyan(
                                " region groups..."
                            )
                        )
                        buildWorkspace.backfillRegion(regionReplacements)
                        terminal.println(green("Region backfill complete."))
                    } catch (e: Exception) {
                        terminal.println(red("Region backfill failed: ${e.stackTraceToString()}"))
                        hasBackfillErrors = true
                    }
                }
            }
            if (datapackReplacements.isNotEmpty()) {
                launch(Dispatchers.IO) {
                    try {
                        terminal.println(
                            cyan("Backfilling ") + (cyan + bold)("${datapackReplacements.size}") + cyan(
                                " datapack groups..."
                            )
                        )
                        buildWorkspace.backfillDatapack(datapackReplacements)
                        terminal.println(green("Datapack backfill complete."))
                    } catch (e: Exception) {
                        terminal.println(red("Datapack backfill failed: ${e.stackTraceToString()}"))
                        hasBackfillErrors = true
                    }
                }
            }
        }

        if (hasBackfillErrors) {
            terminal.println(yellow("Build completed with errors. Check logs above."))
        } else {
            terminal.println(
                green("Build complete. Translated world at ") + (green + bold)(
                    "$targetDir"
                )
            )
        }
    }
}
