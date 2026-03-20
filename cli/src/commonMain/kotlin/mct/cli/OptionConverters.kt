package mct.cli

import com.github.ajalt.clikt.parameters.options.NullableOption
import com.github.ajalt.clikt.parameters.options.convert
import okio.Path.Companion.toPath

fun NullableOption<String, String>.path() = convert { it.toPath() }
