package mct.cli.cmd.kits

import arrow.core.raise.Raise
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles.bold
import com.github.ajalt.mordant.terminal.Terminal
import mct.MCTError
import mct.cli.*
import mct.kit.TranslationMapping
import mct.model.patch.Patch
import mct.model.patch.PatchValidationFailureStrategy
import mct.model.patch.PathKind
import mct.patch.HashValidatingFailure
import mct.patch.applyPatch
import mct.patch.createPatch
import mct.util.io.readCbor
import mct.util.io.readJson
import mct.util.io.writeCbor
import mct.util.io.writeJson
import mct.util.unreachable

class PatchCommands : BaseCommand(
    name = "patch", help = "Creating or applying patch"
) {
    init {
        subcommands(CreatePatch(), ApplyPatch())
    }
}

private class CreatePatch : WorkspaceCommand(name = "create", help = "Creates a new patch") {
    val pattern by withPattern()
    val mappingFile by option("-m", "--mapping", help = "Path to mapping json file").path().required()
    val kind by option("-k", "--kind", help = "Kind of patches").enum<PathKind>(ignoreCase = true).default(Immediate)

    val patchFormat by option("-f", "--patch-format", help = "Format of patches").choice("json", "cbor")
        .default("json")

    val validation by option(
        "--validation", help = "whether to include the validation information"
    ).flag("--no-validation", default = true, defaultForHelp = "enable")

    val output by option("-o", "--output", help = "Output path to the patch file").path().required()

    context(_: Raise<MCTError>)
    override suspend fun App() {
        val mapping = mappingFile.readJson<TranslationMapping>()
        val patch = workspace.createPatch(pattern, mapping, kind, validation)
        when (patchFormat) {
            "json" -> output.writeJson(patch, false)
            "cbor" -> output.writeCbor(patch)
            else -> unreachable
        }
        printlnGreen("Patch was successfully created")
    }
}

private class ApplyPatch : WorkspaceCommand(name = "apply", help = "Apply a patch") {
    val patchFile by option("--patch", "-p", help = "Path to patch file").path().required()

    val patchFormat by option("-f", "--patch-format", help = "Format of patches").choice("json", "cbor")
        .default("json")
    val validationStrategy by option(
        "--validation-strategy", help = "The strategy when the validation of the path fails"
    ).enum<PatchValidationFailureStrategy>(ignoreCase = true).default(Failure)

    context(_: Raise<MCTError>)
    override suspend fun App() {
        val patch = when (patchFormat) {
            "json" -> patchFile.readJson<Patch>()
            "cbor" -> patchFile.readCbor<Patch>()
            else -> unreachable
        }
        when (val result = workspace.applyPatch(patch, validationStrategy)) {
            is Success -> {
                if (result.warnings.isEmpty()) {
                    printlnGreen("Patch was successfully applied")
                } else {
                    printlnYellow("Patch was successfully applied with some warnings as the following")
                    result.warnings.display(terminal, true)
                }
            }

            is ValidationFailure -> {
                printlnRed("The patch has been failed to apply due to the following errors:")
                result.errors.display(terminal, false)
            }
        }
    }
}

private fun List<HashValidatingFailure>.display(terminal: Terminal, isWarning: Boolean = false) = forEach { failure ->
    val message = when (failure) {
        is Missing -> "Missing file: ${bold(failure.key)}"
        is Redundant -> "Redundant file: ${bold(failure.key)}"
        is Unmatched -> "Unmatched file: ${bold(failure.key)}, expected: ${bold(failure.expected)}, but got ${
            bold(failure.actual)
        }"
    }
    val color = if (isWarning) TextColors.yellow else TextColors.red
    terminal.println(color(message))
}