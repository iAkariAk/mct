package mct.cli

import okio.Path

context(cmd: BaseCommand)
suspend fun createCache(relativePath: String): Path =
    (cmd.cacheDir / relativePath).also { cmd.fs.createDirectories(it.parent ?: cmd.cacheDir) }
