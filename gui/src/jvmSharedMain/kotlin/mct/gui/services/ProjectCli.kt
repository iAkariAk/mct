package mct.gui.services

import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import mct.Env
import mct.LoggerLevel
import mct.cli.MCT
import mct.gui.platform.ioDispatcher
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.nio.charset.Charset

/**
 * Run one `mct` command in process.
 *
 * Android and desktop share this byte for byte: the CLI is a JVM library and the pieces the swap
 * below needs — `System.setOut`, `PrintStream`, the default charset — all exist on Android.
 *
 * The caller spells out every path the command needs, `--project-dir` included; nothing is appended
 * behind its back, so what the console reports as `CLI > mct …` is exactly what ran.
 */
context(env: Env)
internal actual suspend fun runCliCommand(arguments: List<String>) {
    val cliArguments = arguments + listOf(
        // The CLI is silent by default (`ColorTerminalLogger(emptyList())` drops everything); its
        // info and warning lines are the progress detail this console exists for.
        "-l", "Info", "-l", "Warning", "-l", "Error",
    )
    env.logger.info { "CLI > mct ${cliArguments.joinToString(" ")}" }
    withContext(ioDispatcher) {
        // The CLI prints through its own terminal, which writes to `System.out`; in process that is
        // the GUI's stdout, so everything the command reports (progress, counts, errors) would be
        // invisible. Its output is routed into the GUI console for the duration of the run.
        //
        // `System.out` is global, so the swap is serialized: a cancelled command's teardown must not
        // run between a successor's swap in and its own, which would either route the second run's
        // output nowhere or leave stdout bound to a dead capture stream.
        cliRunLock.withLock {
            val originalOut = System.out
            val originalErr = System.err
            val console = PrintStream(LineCollectingStream { line -> logCliLine(env, line) }, true)
            System.setOut(console)
            System.setErr(console)
            try {
                // ANSI is forced on: a colour is the only signal that a line the CLI printed
                // through `Terminal.println` is an error or a warning, and the capture turns it
                // into a level.
                MCT()
                    .apply { configureContext { terminal = Terminal(ansiLevel = AnsiLevel.ANSI16) } }
                    .main(cliArguments.toTypedArray())
                // The CLI's own logger prints from its own single-thread dispatcher, so its last
                // lines can arrive after the command returns; without this pause they would go to
                // the real stdout and never reach the console.
                delay(150)
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuntimeException) {
                // Clikt reports a usage error by "exiting"; in process that surfaces as an exception
                // whose type is private to the CLI module and whose message is only the status code
                // (`Exit with status 1`). The parse error itself has already been written to the
                // captured console above, so the useless wording and a stack trace must not become
                // the message the operator sees.
                throw if (e::class.simpleName == "CliExit") {
                    IllegalStateException("CLI 参数错误（${e.message}），详见上方控制台输出", e)
                } else {
                    e
                }
            } finally {
                System.out.flush()
                System.err.flush()
                System.setOut(originalOut)
                System.setErr(originalErr)
            }
        }
    }
}

/**
 * Serializes the `System.out` swap around a CLI run.
 *
 * A [Mutex] rather than a JVM lock on purpose: the run suspends, so it can resume on another
 * thread, and a thread-affine lock could not be released by that thread.
 */
private val cliRunLock = Mutex()

/** SGR escape sequences; stripped for display, read to recover the level. */
private val AnsiEscape = Regex("\u001B\\[[0-9;]*[A-Za-z]")

/** The level prefixes the CLI's `ColorTerminalLogger` writes (`LoggerLevel.prefix`). */
private val LevelPrefixes = listOf(
    "[INFO]" to LoggerLevel.Info,
    "[DEBUG]" to LoggerLevel.Debug,
    "[WARN]" to LoggerLevel.Warning,
    "[ERROR]" to LoggerLevel.Error,
)

/**
 * Route one captured CLI line to the console at the level it reports.
 *
 * The CLI states a level in one of two ways: its logger prefixes `[INFO]`/`[WARN]`/…, and its plain
 * `Terminal.println` calls colour the whole line (red for an error, yellow for a warning, green and
 * blue for progress). The prefix wins when both are present; anything else is informational.
 *
 * The prefix itself is stripped: the console renders the level as its own badge, so keeping the text
 * would print it twice.
 */
private fun logCliLine(env: Env, raw: String) {
    val text = raw.replace(AnsiEscape, "").trimEnd()
    if (text.isBlank()) return
    val prefixed = LevelPrefixes.firstOrNull { text.startsWith(it.first) }
    val level = prefixed?.second ?: levelFromAnsi(raw) ?: LoggerLevel.Info
    val message = prefixed?.let { text.removePrefix(it.first).trimStart() } ?: text
    env.logger.log(level, message)
}

/**
 * The level behind the colours in [raw], most severe first so a line that highlights a number in
 * red still reads as an error.
 */
private fun levelFromAnsi(raw: String): LoggerLevel? {
    val codes = AnsiEscape.findAll(raw)
        .flatMap { match ->
            match.value.removePrefix("\u001B[").removeSuffix("m")
                .split(';')
                .mapNotNull(String::toIntOrNull)
        }
        .toList()
    return when {
        codes.any { it == 31 || it == 91 } -> LoggerLevel.Error
        codes.any { it == 33 || it == 93 } -> LoggerLevel.Warning
        codes.any { it == 90 } -> LoggerLevel.Debug
        codes.any { it in setOf(32, 34, 35, 36, 92, 94, 95, 96) } -> LoggerLevel.Info
        else -> null
    }
}

/**
 * An [OutputStream] that hands over complete lines. Mordant renders one line per write, but nothing
 * guarantees that, so the tail of a partial line is kept until its newline arrives.
 *
 * Collected as bytes, not chars: the `PrintStream` in front encodes text with the platform charset
 * and hands over the encoded bytes, so a byte-to-char conversion would split every multi-byte
 * character (Chinese paths and reasoning text would reach the console as mojibake). Each line is
 * decoded with that same charset.
 */
private class LineCollectingStream(private val onLine: (String) -> Unit) : OutputStream() {
    private val buffer = ByteArrayOutputStream()

    // The CLI writes from its own threads (its logger has a dispatcher of its own), so writes are
    // serialized to keep lines whole.
    @Synchronized
    override fun write(b: Int) {
        if (b == NEWLINE) {
            onLine(buffer.toString(Charset.defaultCharset()))
            buffer.reset()
        } else if (b != CARRIAGE_RETURN) {
            buffer.write(b)
        }
    }

    @Synchronized
    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        for (i in offset until offset + length) write(bytes[i].toInt())
    }

    private companion object {
        const val NEWLINE = '\n'.code
        const val CARRIAGE_RETURN = '\r'.code
    }
}
