package mct.util.translator

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.EncodingType

private val registry= Encodings.newDefaultEncodingRegistry()
private val enc= registry.getEncoding(EncodingType.CL100K_BASE)

internal actual fun calculateToken(str: String): Int =
    enc.countTokens(str)
