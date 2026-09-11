package mct.gui.services

import mct.Env
import mct.MCTPattern
import mct.cext.CextPattern
import mct.command.*
import mct.dp.compile
import mct.dp.mcjson.BuiltinMCJsonPatterns
import mct.gui.model.MCTPatternSlot
import mct.gui.model.MCTPatternState
import mct.nbt.BuiltinNbtPatterns
import mct.pointer.DataPointerPattern
import mct.serializer.MCTJson
import okio.Path.Companion.toPath

private inline fun <reified T> readPatternJson(env: Env, path: String): T? =
    path.takeIf(String::isNotBlank)
        ?.let { env.fs.read(it.toPath()) { readUtf8() } }
        ?.let { MCTJson.decodeFromString<T>(it) }

/**
 * 把界面上配置的每一类规则组装为 [MCTPattern]，是所有提取/补丁入口的唯一构造点。
 *
 * 语义与 CLI 的 `--pattern-*` / `--disable-builtin-*` / `--disable-filter-*` 对齐：
 * 过滤关闭 → 该类规则为 `null`（提取全部）；仅自定义 → 不合并内置规则。
 */
context(env: Env)
fun composePattern(patterns: MCTPatternState): MCTPattern {
    fun MCTPatternSlot.pointers(): List<DataPointerPattern>? =
        readPatternJson<List<DataPointerPattern>>(env, patterns[this].path)

    fun MCTPatternSlot.resolve(builtin: List<DataPointerPattern>): List<DataPointerPattern>? {
        val entry = patterns[this]
        if (!entry.filtering) return null
        val custom = pointers().orEmpty()
        return if (entry.useBuiltin) builtin + custom else custom
    }

    val commandEntry = patterns[MCTPatternSlot.Command]
    val command = readPatternJson<List<CommandExtractPattern>>(env, commandEntry.path)
        ?.compile(commandEntry.useBuiltin)
        ?: BuiltinCommandPatterns

    val componentEntry = patterns[MCTPatternSlot.CommandComponent]
    val commandComponent = readPatternJson<List<ComponentPattern>>(env, componentEntry.path)
        ?.let { if (componentEntry.useBuiltin) BuiltinMinecraftComponentPatterns + it else it }
        ?: BuiltinMinecraftComponentPatterns

    return MCTPattern(
        nbt = MCTPatternSlot.Nbt.resolve(BuiltinNbtPatterns),
        mcjson = MCTPatternSlot.McJson.resolve(BuiltinMCJsonPatterns),
        command = command,
        commandData = MCTPatternSlot.CommandData.resolve(BuiltinCommandDataPatterns),
        commandComponent = commandComponent,
        commandRegex = readPatternJson<List<CommandRegexPattern>>(env, patterns[MCTPatternSlot.CommandRegex].path)
            .orEmpty(),
        cext = readPatternJson<CextPattern>(env, patterns[MCTPatternSlot.Cext].path),
    )
}
