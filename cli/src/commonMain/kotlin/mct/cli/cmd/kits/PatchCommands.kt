package mct.cli.cmd.kits

import arrow.core.raise.Raise
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.mordant.rendering.TextStyles
import mct.MCTError
import mct.cli.*
import mct.kit.TranslationMapping
import mct.model.patch.Patch
import mct.model.patch.PatchValidationFailureStrategy
import mct.model.patch.PathKind
import mct.patch.applyPatch
import mct.patch.createPatch
import mct.util.io.readJson
import mct.util.io.writeJson

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

    val validation by option(
        "--validation", help = "whether to include the validation information"
    ).flag("--no-validation", default = true, defaultForHelp = "enable")

    val output by option("-o", "--output", help = "Output path to the patch file").path().required()

    context(_: Raise<MCTError>)
    override suspend fun App() {
        val mapping = mappingFile.readJson<TranslationMapping>()
        val patch = workspace.createPatch(pattern, mapping, kind, validation)
        output.writeJson(patch)
        printlnGreen("Patch was successfully created")
    }
}

private class ApplyPatch : WorkspaceCommand(name = "apply", help = "Apply a patch") {
    val patchFile by option("--patch", "-p", help = "Path to patch file").path().required()
    val validationStrategy by option(
        "--validation-strategy", help = "The strategy when the validation of the path fails"
    ).enum<PatchValidationFailureStrategy>(ignoreCase = true).default(Failure)

    context(_: Raise<MCTError>)
    override suspend fun App() {
        val patch = patchFile.readJson<Patch>()
        val result = workspace.applyPatch(patch, validationStrategy)
        when (result) {
            is Success -> {

                if (result.warning.isEmpty()) {
                    printlnGreen("Patch was successfully applied")
                } else {
                    printlnYellow("Patch was successfully applied with some unmatched as the following")
                    result.warning.forEach { (path, reason) ->
                        val (expected, actual) = reason
                        printlnYellow("${TextStyles.bold(path)}: expect $expected, but got $actual")
                    }
                }
            }

            is ValidationFailure -> {
                printlnRed("The patch has been failed to apply due to the following unmatched:")
                result.unmatched.forEach { (path, reason) ->
                    val (expected, actual) = reason
                    printlnRed("${TextStyles.bold(path)}: expect $expected, but got $actual")
                }
            }
        }
    }
}