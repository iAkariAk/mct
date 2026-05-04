package mct.dp.mcfunction

import mct.Logger
import mct.util.*

data class MCCommand(
    val raw: String,
    val name: String,
    val indices: IntRange,
    val args: List<Arg>,
    val isMarco: Boolean = false, // begin with `$`
) {
    /** Leading whitespace trimmed from the original line to obtain [raw]. */
    val trimOffset: Int
        get() = (indices.last - indices.first) - raw.length

    /**
     * 1-based index
     */
    operator fun get(position: Int): Arg {
        val index = position - 1
        require(index < args.size) {
            "Position $position out of bounds for length ${args.size}"
        }
        return args[index]
    }

    operator fun contains(arg: String) = args.any { it.content == arg }

    data class Arg(
        val relativeIndices: IntRange,
        override val indices: IntRange,
        override val content: String
    ) : StringIndices

    companion object
}

context(logger: Logger)
fun parseMCFunction(
    mcf: String,
): List<MCCommand> {
    val mcfunctions = mutableListOf<MCCommand>()
    var row = 0

    line@ fun handleLine(lineStartLine: Int, line: String) {
        val chars = line.toCharArray()
        val buffer = StringBuilder()

        var lineBeginMode = true
        var isMarco = false
        var commandName: String? = null
        val args = mutableListOf<MCCommand.Arg>()
        val stateStack = ArrayDeque<State>().apply { push(RootState) }

        fun bindBufferIntoCmd(endCol: Int) {
            val str = buffer.toString()
            buffer.clear()

            if (commandName == null) {
                commandName = str
                return
            }

            val startCol = endCol - str.length + 1 // adding to point the start of str
            args += MCCommand.Arg(
                relativeIndices = startCol..endCol,
                indices = (lineStartLine + startCol)..lineStartLine + endCol,
                content = str
            )
        }

        for ((col, c) in chars.withIndex()) {
            val peekedState = stateStack.peek()
            check(stateStack.bottom() == RootState) {
                "Fatal error due to the RootState being replaced."
            }

            if (lineBeginMode) {
                fun invaliChar() = logger.error {
                    "Invali $c is found at $line, in that what is after the marco should be letter"
                }
                when (c) {
                    '#' -> {
                        if (isMarco) {
                            invaliChar()
                            return@line
                        } else return@line // skip comments
                    }

                    ' ' -> if (isMarco) {
                        invaliChar()
                        return@line
                    } else continue

                    '$' -> {
                        isMarco = true
                        continue
                    }
                }
                lineBeginMode = false

                if (commandName == null) {
                    if (!c.isLetter()) {
                        logger.error {
                            "Invali char($c) is found at $line, in that command should start with letter"
                        }
                        return@line
                    }
                }
            }

            if (peekedState is QuoteState) {
                if (peekedState.isEscaped) {
                    buffer.append(c)
                    peekedState.isEscaped = false
                    continue
                }
                if (c == '\\') {
                    peekedState.isEscaped = true
                    buffer.append(c)
                    continue
                }

                buffer.append(c)
                if (c == peekedState.char) {
                    stateStack.pop()
                }
                continue
            }

            if (c == ' ' && peekedState == RootState) { // cmd argument
                bindBufferIntoCmd(col - 1)
                continue
            }
            buffer.append(c)

            when (c) {
                '\'' -> stateStack.push(QuoteState.SingleQuote())
                '\"' -> stateStack.push(QuoteState.DoubleQuote())

                '{' -> stateStack.push(BracketState.Curly)
                '[' -> stateStack.push(BracketState.Square)
                '}', ']' -> {
                    val excepted = if (c == '}') BracketState.Curly else BracketState.Square
                    if (peekedState != excepted) {
                        logger.error {
                            "The excepted terminator is $excepted but actual $peekedState"
                        }
                        return@line
                    }

                    stateStack.pop()
                }
            }
        }

        if (buffer.isNotEmpty()) {
            bindBufferIntoCmd(chars.size - 1)
        }

        val peekedState = stateStack.peek()
        if (peekedState != RootState) {
            val operator = when (peekedState) {
                is QuoteState -> peekedState.char.toString()
                is BracketState -> "${peekedState.left}${peekedState.right}"
            }
            logger.error {
                "The operator '$operator' hasn't been closed correctly"
            }
        }
        if (commandName == null) return
        val mcfunction = MCCommand(
            line.trimStart(),
            commandName,
            lineStartLine..(lineStartLine + line.length),
            args,
            isMarco
        )
        mcfunctions += mcfunction
    }


    val line = StringBuilder()
    var lastC: Char? = null
    var lineStart = 0
    for ((charOffset, c) in mcf.toCharArray().withIndex()) {
        if (c == '\n') {
            handleLine(lineStart, line.toString())
            line.clear()
            lineStart = charOffset + 1
            row++
            lastC = c
            continue
        }
        if (lastC == '\r') {
            handleLine(lineStart, line.toString())
            line.clear()
            lineStart = charOffset
            row++
            line.append(c)
            lastC = c
            continue
        }

        if (c == '\r') {
            lastC = c
            continue
        }

        line.append(c)

        lastC = c
    }
    handleLine(lineStart, line.toString())

    return mcfunctions
}

private sealed interface State

private data object RootState : State

private sealed class QuoteState(val char: Char, var isEscaped: Boolean = false) : State {
    class SingleQuote : QuoteState('\'')
    class DoubleQuote : QuoteState('\"')

    override fun toString(): String {
        return "QuoteState(char=$char, isEscaped=$isEscaped)"
    }
}

private enum class BracketState(val left: Char, val right: Char) : State {
    //    Parenthesis,
    Square('[', ']'),
    Curly('{', '}'),
}
