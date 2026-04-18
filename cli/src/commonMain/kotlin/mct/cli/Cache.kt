package mct.cli

import okio.Path

context(cmd: BaseCommand)
fun createCache(relativePath: String): Path =
    (cmd.cacheDir / relativePath).also { cmd.fs.createDirectories(it.parent ?: cmd.cacheDir) }
