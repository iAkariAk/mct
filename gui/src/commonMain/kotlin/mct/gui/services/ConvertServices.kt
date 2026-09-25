package mct.gui.services

import mct.Env
import mct.gui.model.ConvertFormat
import mct.gui.model.ConvertToolState

/**
 * Convert files between NBT, SNBT and JSON by running `mct kit convert` in process.
 *
 * The conversion itself is the CLI's (`ConvertCommand`): format detection, IR decoding and NBT
 * compression are not re-implemented here, and the console shows the exact command line that ran.
 * Options left at their `auto` default are not passed, so the CLI's own default applies.
 *
 * [ConvertCompression.None] is passed explicitly rather than omitted: the CLI's default is the same
 * value, but spelling it out keeps the console line a complete record of what ran.
 */
context(env: Env)
suspend fun convertFormats(state: ConvertToolState) {
    val arguments = buildList {
        add("kit")
        add("convert")
        add("--input")
        add(state.input)
        if (state.batch) {
            // Batch mode converts every file the pattern matches and has no single output path; the
            // CLI rejects `--output` together with `--regex`.
            add("--regex")
            add("--current")
            add(state.currentDirectory)
        } else {
            add("--output")
            add(state.output)
        }
        if (state.inputFormat != ConvertFormat.Auto) {
            add("--input-format")
            add(state.inputFormat.key)
        }
        if (state.outputFormat != ConvertFormat.Auto) {
            add("--output-format")
            add(state.outputFormat.key)
        }
        add("--compression")
        add(state.compression.key)
        // Blank means "the CLI's default level", which is not a value the flag can express.
        state.compressionLevel.toIntOrNull()?.let {
            add("--compression-level")
            add(it.toString())
        }
        if (state.pretty) add("--pretty")
    }
    runCliCommand(arguments)
    env.logger.info {
        if (state.batch) {
            "已转换 ${state.currentDirectory} 下匹配「${state.input}」的文件"
        } else {
            "已转换: ${state.input} -> ${state.output}"
        }
    }
}
