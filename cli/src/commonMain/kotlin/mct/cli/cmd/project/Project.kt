package mct.cli.cmd.project

import arrow.core.raise.Raise
import com.aallam.openai.api.logging.LogLevel
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
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
import mct.cli.*
import mct.cli.util.CURRENT_PATH
import mct.command.BuiltinCommandDataPatterns
import mct.command.BuiltinCommandPatterns
import mct.command.CommandExtractPattern
import mct.command.CommandRegexPattern
import mct.dp.backfillDatapack
import mct.dp.compile
import mct.dp.extractFromDatapack
import mct.dp.mcjson.BuiltinMCJPatterns
import mct.extra.ai.AiSign
import mct.extra.ai.ChatCompletionCall
import mct.extra.ai.translator.*
import mct.kit.TranslationMapping
import mct.kit.TranslationPool
import mct.kit.exportIntoPool
import mct.model.patch.*
import mct.mtl.MTLX
import mct.mtl.translateByMTLX
import mct.nbt.BuiltinNbtPatterns
import mct.pointer.CustomizedDataPointerPattern
import mct.region.backfillRegion
import mct.region.extractFromRegion
import mct.util.io.copyToRecursively
import mct.util.io.readJson
import mct.util.io.readText
import mct.util.io.writeJson
import okio.Path
import okio.Path.Companion.toPath

private const val POOL_CACHE = "all_texts.json"
private const val REGION_EXTRACTION_CACHE = "region_extractions.json"
private const val DATAPACK_EXTRACTION_CACHE = "datapack_extractions.json"
private const val MISSING = "missing.json"
private const val REGION_REPLACEMENTS = "region_replacements.json"
private const val DATAPACK_REPLACEMENTS = "datapack_replacements.json"

class Project : SuspendingCliktCommand(name = "project") {
    init {
        subcommands(Init(), Update(), TermExtract(), Translate(), Build())
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
            printlnRed("Failed to copy world: ${e.message ?: "unknown error"}")
            panic("Failed to copy world: ${e.message ?: "unknown error"}")
        }
        val config = ProjectConfig(
            name = projectName,
        )
        (projectDir / "mct.toml").writeToml(config)
        printlnGreen("Project '$projectName' created at $projectDir")
    }
}

private abstract class ProjectCommand(name: String? = null, help: String? = null) : BaseCommand(name, help) {
    val projectDir = Path.CURRENT_PATH
    val projectConfig by lazy {
        val toml = projectDir / "mct.toml"
        if (!fs.exists(toml)) {
            panic("Not a project directory (mct.toml not found). Run 'project init' first.")
        }
        toml.readToml<ProjectConfig>()
    }
    val patterns get() = projectConfig.patterns
    val ai by lazy {
        if (projectConfig.ai.token.isBlank() || projectConfig.ai.token == AIConfig.Default.token) {
            panic("AI token not configured. Set [ai.token] in mct.toml")
        }
        projectConfig.ai
    }
    val srcDir = projectDir / "src"
    val missingFile = projectDir / MISSING
    val poolFile by lazy { cache(POOL_CACHE) }
    val regionExtractionFile by lazy { cache(REGION_EXTRACTION_CACHE) }
    val datapackExtractionFile by lazy { cache(DATAPACK_EXTRACTION_CACHE) }
    val termsFile get() = projectDir / projectConfig.terms
    val mappingFile get() = projectDir / projectConfig.mappings
    val mtlxFile get() = projectConfig.mtlx?.let { projectDir / it }

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

    context(_: Raise<MCTError>)
    fun ensureExtracted() {
        if (!fs.exists(regionExtractionFile) && !fs.exists(datapackExtractionFile) && !fs.exists(poolFile)) {
            panic("No extractions found in cache. Run `project update` first.")
        }
    }

    context(_: Raise<MCTError>)
    suspend fun createCall() = context(env) {
        ChatCompletionCall(
            apiUrl = ai.apiUrl,
            token = ai.token,
            model = ai.model,
            useStreamApi = ai.useStreamApi,
            temperature = ai.temperature,
            logLevel = if (ai.enableHttpLogging) LogLevel.All else LogLevel.None,
        )
    }

    fun registerLLMOutput(): LLMOutput {
        val output = object : LLMOutput {
            override var totalConsumedTokenCount = 0
            override val thinkings = mutableMapOf<Int, StringBuilder>()
        }
        NotifierHooks.onAiSign {
            when (it) {
                is AiSign.Reasoning if ai.enableThinkingOutput -> {
                    val sb = output.thinkings.getOrPut(it.id) { StringBuilder() }
                    if (it.terminated) {
                        val content = sb.toString()
                        if (content.isNotEmpty()) {
                            terminal.println(
                                Panel(
                                    title = Text(blue("Thinking (${it.id})")),
                                    content = Text(content),
                                    bottomTitle = Text(green("Thinking finished.")),
                                    bottomTitleAlign = TextAlign.RIGHT,
                                )
                            )
                        }
                        output.thinkings.remove(it.id)
                    } else {
                        sb.append(it.reasoningContent)
                    }
                }

                is AiSign.ConsumeToken -> {
                    output.totalConsumedTokenCount += it.count
                }

                else -> Unit
            }
        }
        return output
    }
}

private interface LLMOutput {
    val totalConsumedTokenCount: Int
    val thinkings: Map<Int, StringBuilder>
}


private class Update : ProjectCommand("update", "Update extraction pool") {
    context(_: Raise<MCTError>)
    override suspend fun App() {
        fun requirePath(path: String, label: String): Path {
            val p = path.toPath()
            if (!fs.exists(p)) panic("$label pattern file not found: $path")
            return p
        }

        val regionPatterns = patterns.nbt.flatMap {
            requirePath(it, "Region").readJson<List<CustomizedDataPointerPattern>>().map { c -> c.compile() }
        }.ifEmpty { BuiltinNbtPatterns }

        val commandPatterns = patterns.command.flatMap {
            requirePath(it, "Command").readJson<List<CommandExtractPattern>>()
        }.let { if (it.isEmpty()) BuiltinCommandPatterns else it.compile() }

        val commandDataPatterns = patterns.commandData.flatMap {
            requirePath(it, "Command data").readJson<List<CustomizedDataPointerPattern>>().map { c -> c.compile() }
        }.ifEmpty { BuiltinCommandDataPatterns }

        val mcjPatterns = patterns.mcjson.flatMap {
            requirePath(it, "MCJson").readJson<List<CustomizedDataPointerPattern>>().map { c -> c.compile() }
        }.ifEmpty { BuiltinMCJPatterns }

        val commandRegexPatterns = patterns.commandRegex.flatMap {
            requirePath(it, "Command Regex").readJson<List<CommandRegexPattern>>()
        }

        val w = workspace()

        val existingMapping = if (fs.exists(mappingFile)) {
            mappingFile.readJson<TranslationMapping>()
        } else {
            emptyMap()
        }

        val pool = coroutineScope {
            val regionJob = async(Dispatchers.IO) {
                val groups = w.extractFromRegion(
                    MCTPattern(
                        nbt = regionPatterns,
                        command = commandPatterns,
                        commandData = commandDataPatterns,
                        commandRegex = commandRegexPatterns
                    )
                ).toList()
                cache(REGION_EXTRACTION_CACHE).writeJson<List<ExtractionGroup>>(groups, projectConfig.prettyJson)
                printlnGreen("Extracted " + bold("${groups.size}") + " groups from region")
                groups
            }
            val datapackJob = async(Dispatchers.IO) {
                val groups = w.extractFromDatapack(
                    MCTPattern(
                        command = commandPatterns,
                        commandData = commandDataPatterns,
                        mcjson = mcjPatterns,
                        commandRegex = commandRegexPatterns
                    )
                ).toList()
                cache(DATAPACK_EXTRACTION_CACHE).writeJson<List<ExtractionGroup>>(groups, projectConfig.prettyJson)
                printlnGreen("Extracted " + bold("${groups.size}") + " groups from datapack")
                groups
            }
            (regionJob.await() + datapackJob.await()).exportIntoPool(simply = false)
        }
        cache(POOL_CACHE).writeJson(pool, projectConfig.prettyJson)
        val missingPool = pool.filter { it !in existingMapping }
        if (missingPool.isNotEmpty()) {
            printlnYellow("Missing " + bold("${missingPool.size}") + " items (${pool.size} total extracted)")
            missingFile.writeJson(missingPool, projectConfig.prettyJson)
        } else {
            printlnGreen("No new items found (${pool.size} total extracted, all mapped)")
        }
    }
}

private class TermExtract : ProjectCommand("term", "Extract terms via AI") {
    context(_: Raise<MCTError>)
    override suspend fun App() {
        ensureExtracted()

        if (!fs.exists(missingFile)) {
            panic("No $missingFile can be extracted")
        }
        val missingPool = missingFile.readJson<TranslationPool>()

        val termsFile = projectDir / projectConfig.terms
        val existingTerms = if (fs.exists(termsFile)) {
            termsFile.readJson<TermTable>()
        } else emptyMap()

        terminal.println(cyan("Loaded ${existingTerms.size} existing terms"))

        val extractor = TermExtractor(
            call = createCall(),
            defaultTerms = existingTerms,
            targetLanguage = ai.targetLanguage,
            tokenThreshold = ai.tokenThreshold,
            literatureStyle = ai.literatureStyle,
            concurrency = ai.concurrency
        )

        val output = registerLLMOutput()

        terminal.println(cyan("Starting extraction using ${bold(ai.model)} model and ${bold(ai.concurrency.toString())} concurrency..."))
        val terms = extractor.extract(missingPool) { salvaged ->
            termsFile.writeJson(existingTerms + salvaged, projectConfig.prettyJson)

            if (salvaged.isNotEmpty()) {
                termsFile.writeJson(salvaged, projectConfig.prettyJson)
            }

            printlnRed(
                "Extraction was cancelled; salvaged ${bold(salvaged.size.toString())} terms to $termsFile."
            )
        }

        printlnGreen("Extracted " + bold("${terms.size - existingTerms.size}") + " new terms")

        if (terms.isNotEmpty()) {
            termsFile.writeJson(terms, projectConfig.prettyJson)
            printlnGreen("${terms.size} terms saved to $termsFile")
        }

        printlnGreen("Extraction completed, consumed " + bold("${output.totalConsumedTokenCount}") + " tokens.")
    }
}


private class Translate : ProjectCommand("translate", "Translate extractions via AI") {
    context(_: Raise<MCTError>)
    override suspend fun App() {
        ensureExtracted()

        val extractionGroups = mutableListOf<ExtractionGroup>()
        if (fs.exists(regionExtractionFile)) {
            extractionGroups += regionExtractionFile.readJson<List<ExtractionGroup>>()
            printlnGreen("Loaded region extractions")
        }
        if (fs.exists(datapackExtractionFile)) {
            extractionGroups += datapackExtractionFile.readJson<List<ExtractionGroup>>()
            printlnGreen("Loaded datapack extractions")
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

        val existingTerms = if (fs.exists(termsFile)) {
            termsFile.readJson<TermTable>()
        } else emptyMap()
        terminal.println(cyan("Loaded ${existingTerms.size} existing terms"))

        val translator = Translator(
            call = createCall(),
            customizedPrompts = CustomizedPrompts(
                literatureStyle = ai.literatureStyle,
                targetLanguage = ai.targetLanguage,
                handleGradientAggressively = ai.handleGradientAggressively,
            ),
            defaultTerms = existingTerms,
            tokenThreshold = ai.tokenThreshold,
            concurrency = ai.concurrency
        )

        val output = registerLLMOutput()

        terminal.println(cyan("Starting translation using ${bold(ai.model)} model and ${bold(ai.concurrency.toString())} concurrency..."))
        val mapping = translator.translate(extractionGroups, existingMapping, ai.concurrentByKind) { terms, salvaged ->
            mappingFile.writeJson(existingMapping + salvaged, projectConfig.prettyJson)
            printlnGreen("Mapping saved to $mappingFile")

            if (translator.terms.isNotEmpty()) {
                termsFile.writeJson(translator.terms, projectConfig.prettyJson)
            }

            printlnRed(
                "Translation was cancelled; salvaged ${bold(terms.size.toString())} terms and ${
                    bold(salvaged.size.toString())
                } translated items."
            )
        }

        val totalMapping = existingMapping + mapping
        printlnGreen("Translated " + bold("${mapping.size}") + " new items (${totalMapping.size} total)")

        mappingFile.writeJson(totalMapping, projectConfig.prettyJson)
        printlnGreen("Mapping saved to $mappingFile")

        if (translator.terms.isNotEmpty()) {
            termsFile.writeJson(translator.terms, projectConfig.prettyJson)
            printlnGreen("${translator.terms.size} terms saved to $termsFile")
        }

        printlnGreen("Translation completed, consumed " + bold("${output.totalConsumedTokenCount}") + " tokens.")
    }
}


private class Build : ProjectCommand("build", "Build translated world") {
    context(_: Raise<MCTError>)
    override suspend fun App() {
        ensureExtracted()

        val mappingFile = projectDir / projectConfig.mappings
        if (!fs.exists(mappingFile)) {
            panic("No mapping found. Run `project translate` first.")
        }

        val mapping = mappingFile.readJson<MutableMap<String, String?>>()
        terminal.println(
            cyan("Loaded mapping with ") + (cyan + bold)("${mapping.size}") + cyan(
                " entries"
            )
        )

        if (mtlxFile != null) {
            val mtlx = MTLX.fromString(mtlxFile!!.readText())
            printlnGreen("Compile ${bold(mtlx.mtlMappings.size.toString())} mtl items and ${bold(mtlx.rawMappings.size.toString())} raw item.")
            val pool = poolFile.readJson<TranslationPool>()
            val extraMapping = pool.translateByMTLX(mtlx).filterValues { it != null }
            printlnGreen("Reinsert ${extraMapping.size} items by $mtlxFile")
            mapping += extraMapping
        }

        cache("build_mappings.json").writeJson(mapping)

        val regionGroups = if (fs.exists(regionExtractionFile)) {
            regionExtractionFile.readJson<List<ExtractionGroup>>()
        } else {
            emptyList()
        }
        val datapackGroups = if (fs.exists(datapackExtractionFile)) {
            datapackExtractionFile.readJson<List<ExtractionGroup>>()
        } else {
            emptyList()
        }
        if (regionGroups.isEmpty() && datapackGroups.isEmpty()) {
            panic("All cache files are empty. Run 'project update' first.")
        }

        val regionReplacements = if (regionGroups.isNotEmpty()) {
            @Suppress("UNCHECKED_CAST") (regionGroups.replace(mapping) as List<RegionReplacementGroup>).also {
                cache(REGION_REPLACEMENTS).writeJson<List<ReplacementGroup>>(it, projectConfig.prettyJson)
                printlnGreen("Generated " + bold("${it.size}") + " region replacement groups")
            }
        } else {
            emptyList()
        }

        val datapackReplacements = if (datapackGroups.isNotEmpty()) {
            @Suppress("UNCHECKED_CAST") (datapackGroups.replace(mapping) as List<DatapackReplacementGroup>).also {
                cache(DATAPACK_REPLACEMENTS).writeJson<List<ReplacementGroup>>(it, projectConfig.prettyJson)
                printlnGreen("Generated " + bold("${it.size}") + " datapack replacement groups")
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
            printlnRed("Failed to copy world: ${e.message ?: "unknown error"}")
            panic("Failed to copy world: ${e.message ?: "unknown error"}")
        }
        printlnGreen("World copied.")

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
                        printlnGreen("Region backfill complete.")
                    } catch (e: Exception) {
                        printlnRed("Region backfill failed: ${e.stackTraceToString()}")
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
                        printlnGreen("Datapack backfill complete.")
                    } catch (e: Exception) {
                        printlnRed("Datapack backfill failed: ${e.stackTraceToString()}")
                        hasBackfillErrors = true
                    }
                }
            }
        }

        if (hasBackfillErrors) {
            printlnYellow("Build completed with errors. Check logs above.")
        } else {
            printlnGreen("Build complete. Translated world at " + bold("$targetDir"))
        }
    }
}
