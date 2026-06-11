package mct.cli.util

import okio.Path

val Path.Companion.CURRENT_PATH get() = ".".toPath(true)