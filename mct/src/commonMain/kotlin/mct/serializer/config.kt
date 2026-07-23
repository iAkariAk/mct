package mct.serializer

import kotlinx.serialization.json.Json
import mct.command.extractPatternModule
import net.benwoodworth.knbt.Nbt
import net.benwoodworth.knbt.NbtCompression
import net.benwoodworth.knbt.NbtVariant
import net.benwoodworth.knbt.StringifiedNbt

val MCTJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    explicitNulls = false
    isLenient = true
    allowStructuredMapKeys = true
    allowComments = true
    allowTrailingComma = true

    serializersModule = extractPatternModule
}

val PrettyJson = Json(MCTJson) {
    prettyPrint = true
    prettyPrintIndent = "  "
}

private val CommonNbt = Nbt {
    variant = NbtVariant.Java
    ignoreUnknownKeys = true
    compression = NbtCompression.None
    mutf8 = true
    encodeDefaults = false
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

val Snbt = StringifiedNbt {}

val PrettySnbt = StringifiedNbt(Snbt) {
    prettyPrint = true
    prettyPrintIndent = "  "
}
