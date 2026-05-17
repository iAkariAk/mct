package mct.dp

import kotlinx.serialization.SerializationException
import mct.MCTError


sealed interface DBError : MCTError

sealed interface BackfillError : DBError {}

sealed interface ExtractError : DBError {
    data class JsonSyntaxError(val exception: SerializationException) : ExtractError {
        override val message = exception.message ?: "<null>"
    }
}

sealed interface MCFunctionExtractError : ExtractError {
}

sealed interface MCJsonExtractError : ExtractError {
    data class JsonSyntaxError(val exception: SerializationException) : MCJsonExtractError {
        override val message = exception.message ?: "<null>"
    }
}
