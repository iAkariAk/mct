package mct.cli.cmd.kits

import arrow.core.raise.Raise
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import kotlinx.schema.generator.json.serialization.SerializationClassJsonSchemaGenerator
import mct.MCTError
import mct.cli.BaseCommand
import mct.cli.path
import mct.command.CommandExtractPattern
import mct.command.CommandRegexPattern
import mct.pointer.CustomizedDataPointerPattern
import mct.serializer.MCTJson
import mct.util.io.writeJson
import mct.util.unreachable

class ExportScheme : BaseCommand("export-scheme", help = "The JSON-scheme generated for kinds of mct pattern") {
    val kind by option("--kind", "-K").choice("command", "data_pointer", "command_regex").required()
    val output by option("--output", "-o", help = "The path to generated JSON Scheme").path().required()
    val pretty by option("--pretty", "-P", help = "Enable pretty JSON output").flag()

    context(_: Raise<MCTError>)
    override suspend fun App() {
        val descriptor = when (kind) {
            "command" -> CommandExtractPattern.serializer().descriptor
            "data_pointer" -> CustomizedDataPointerPattern.serializer().descriptor
            "command_regex" -> CommandRegexPattern.serializer().descriptor
            else -> unreachable
        }
        val generator = SerializationClassJsonSchemaGenerator(json = MCTJson)
        val scheme = generator.generateSchema(descriptor)
        output.writeJson(scheme, pretty)
    }
}