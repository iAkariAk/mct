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
 * Assemble every configured rule category into an [MCTPattern]; the single construction
 * point for all extraction and patch entry points.
 *
 * Semantics match the CLI's `--pattern-*` / `--disable-builtin-*` / `--disable-filter-*`:
 * filter off yields `null` for that category (extract everything), and custom-only means the
 * built-in rules are not merged in.
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
