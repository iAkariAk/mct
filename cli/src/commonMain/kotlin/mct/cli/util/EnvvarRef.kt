package mct.cli.util

import mct.cli.panic
import mct.util.envvar

fun String.evaluateEnvvar(): String = if (startsWith('@')) {
    val name = substring(1)
    envvar(name) ?: panic("Envvar $name don't exist.")
} else this