@file:OptIn(ExperimentalNbtApi::class)

package mct.serializer

import kotlinx.serialization.json.Json
import mct.dp.mcfunction.extractPatternModule
import net.benwoodworth.knbt.*

val MCTJson = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
    ignoreUnknownKeys = true

    serializersModule = extractPatternModule
}

private val CommonNbt= Nbt {
    variant = NbtVariant.Java
    ignoreUnknownKeys = true
    compression = NbtCompression.None
}

val NbtZlib = Nbt(CommonNbt) {
    compression = NbtCompression.Zlib
}

val NbtGzip = Nbt(CommonNbt) {
    compression = NbtCompression.Gzip
}


val NbtNone = Nbt(CommonNbt) {
    compression = NbtCompression.None
}


val Snbt = StringifiedNbt {
    prettyPrint = true
    prettyPrintIndent = "  "
}