package mct.gui.services

import mct.Env
import mct.MCTPattern
import mct.cext.CextPattern
import mct.command.BuiltinCommandDataPatterns
import mct.command.BuiltinCommandPatterns
import mct.command.CommandExtractPattern
import mct.command.CommandRegexPattern
import mct.dp.compile
import mct.dp.mcjson.BuiltinMCJsonPatterns
import mct.gui.model.PatternState
import mct.nbt.BuiltinNbtPatterns
import mct.pointer.DataPointerPattern
import mct.serializer.MCTJson
import okio.Path.Companion.toPath

private inline fun <reified T> readPatternJson(env: Env, path: String): T? =
    path.takeIf(String::isNotBlank)
        ?.let { env.fs.read(it.toPath()) { readUtf8() } }
        ?.let { MCTJson.decodeFromString<T>(it) }

/**
 * 把界面中选择的规则文件与内置规则组合为 [MCTPattern]。
 *
 * [includeRegion] / [includeMcjson] 标记本次操作真正参与的类别，未参与的类别置为 `null`，
 * 避免把无关规则带进提取流程；[disableBuiltinFilter] 为 true 时仅保留自定义规则。
 */
context(env: Env)
fun composePattern(
    patterns: PatternState,
    includeRegion: Boolean = true,
    includeMcjson: Boolean = true,
    disableBuiltinFilter: Boolean = false,
): MCTPattern {
    val extraNbt = readPatternJson<List<DataPointerPattern>>(env, patterns.regionPatternPath)
    val extraMcjson = readPatternJson<List<DataPointerPattern>>(env, patterns.mcjPatternPath)
    val extraCommand = readPatternJson<List<CommandExtractPattern>>(env, patterns.commandPatternPath)
    val extraCommandData = readPatternJson<List<DataPointerPattern>>(env, patterns.commandDataPatternPath)
    val extraCommandRegex = readPatternJson<List<CommandRegexPattern>>(env, patterns.commandRegexPatternPath)
    val cext = readPatternJson<CextPattern>(env, patterns.cextPatternPath)

    return MCTPattern(
        nbt = when {
            !includeRegion -> null
            disableBuiltinFilter -> extraNbt
            else -> BuiltinNbtPatterns + extraNbt.orEmpty()
        },
        mcjson = when {
            !includeMcjson -> null
            disableBuiltinFilter -> extraMcjson
            else -> BuiltinMCJsonPatterns + extraMcjson.orEmpty()
        },
        command = extraCommand?.compile() ?: BuiltinCommandPatterns,
        commandData = when {
            disableBuiltinFilter -> extraCommandData
            else -> BuiltinCommandDataPatterns + extraCommandData.orEmpty()
        },
        commandRegex = extraCommandRegex.orEmpty(),
        cext = cext,
    )
}
