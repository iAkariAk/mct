package mct.cli.render

import com.github.ajalt.mordant.terminal.Terminal

sealed interface Render<T> {
    fun render(value: T, terminal: Terminal, newline: Boolean = true)
}

internal fun Terminal.render(content: String, newline: Boolean = true) =
    if (newline) this.println(content)
    else this.print(content)