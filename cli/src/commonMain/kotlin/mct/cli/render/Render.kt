package mct.cli.render

import com.github.ajalt.mordant.terminal.Terminal

sealed interface Render<T> {
    fun render(value: T, terminal: Terminal): String
}

@PublishedApi
internal fun Terminal.printNewOrNoLine(content: String, newline: Boolean = true) {
    val message = render(content)
    if (newline) this.println(message)
    else this.print(message)
}
